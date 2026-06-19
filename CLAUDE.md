# CLAUDE.md — Live Cam Streaming Platform

## Project Overview

A Chaturbate/Stripchat-class live webcam streaming and real-time interaction platform with a virtual token economy. The authoritative design document is [live-cam-platform-blueprint.md](live-cam-platform-blueprint.md).

**Current phase:** Architecture/design (Phase 0). No production code yet.

---

## Non-Negotiables — Read Before Writing Any Code

These are legal/existential requirements, not optional features:

1. **Age & KYC verification** — every broadcaster must be verified 18+ (2257 compliance). Viewers require verification in jurisdictions like UK (Online Safety Act), US state AV laws, and EU. Use vendors (Yoti, Veriff, Jumio, Persona, AU10TIX) — do not build this.
2. **Real-time content moderation** — AI scanning of live frames + human review is legally required. Use Hive AI / AWS Rekognition + NCMEC integration. Never skip the moderation pipeline in video paths.
3. **High-risk payments only** — Stripe/PayPal cannot be used. Approved acquirers: CCBill, Epoch, SegPay, Verotel, Vendo. Never touch raw card data — PCI-DSS scope is minimized by tokenizing through the vendor.
4. **Double-entry wallet ledger** — token debits/credits use idempotent, strongly-consistent double-entry accounting. Tips must never double-charge or disappear.

---

## Architecture: Three Planes (Never Conflate)

```
Application plane   → Accounts, wallet, catalog, payments   (50–300 ms latency target)
Real-time plane     → Chat, presence, tips, events          (< 200 ms)
Media plane         → Video ingest, transcode, delivery     (glass-to-glass < 1–3 s)
```

These planes scale, fail, and cost independently. Keep their services, data stores, and networking separate.

---

## Technology Stack

### Languages by workload (polyglot is intentional — do not force one language)

| Workload | Language | Reason |
|---|---|---|
| Business logic, wallet, payments, payouts, catalog, admin | Java 21 + Spring Boot | Transactional rigor, JVM ecosystem |
| Real-time, chat, presence, streaming orchestration, gateways | Go | High-concurrency, low GC pause |
| ML, moderation, AI scoring | Python (FastAPI) | ML ecosystem |

### Frontend
- **React + TypeScript + Next.js** (SSR for SEO on discovery pages)
- **Tailwind CSS**
- **React Native** for mobile (or native Swift/Kotlin for camera/WebRTC quality)
- Streaming playback: `livekit-client` SDK + **HLS.js**

### Media Pipeline
- **Phase 1:** Amazon IVS or LiveKit Cloud (managed) — ship fast, avoid SFU ops
- **Phase 3+:** Self-hosted **LiveKit** on Kubernetes; fallback options: mediasoup, Janus
- **Transcoding:** FFmpeg + NVENC (GPU) for ABR renditions
- **Ingest:** RTMP/SRT via nginx-rtmp or LiveKit; WebRTC for browser broadcasters
- **TURN/STUN:** coturn, geographically distributed

### Data Stores (database-per-service pattern)
- **PostgreSQL** — primary OLTP for all transactional services
- **Redis** — cache, presence TTL heartbeats, pub/sub fan-out, leaderboards (sorted sets), rate limiting
- **ScyllaDB / Cassandra** — chat history (high write throughput, time-series)
- **Elasticsearch / OpenSearch** — discovery catalog and search
- **ClickHouse** (or Snowflake) — analytics/BI clickstream
- **S3 + CloudFront/Fastly** — media assets, static, VOD
- **HashiCorp Vault** — secrets; KMS for PII and KYC document encryption

### Messaging
- **Apache Kafka (AWS MSK)** — durable event backbone, transactional outbox, audit trail
- **NATS or Redis Pub/Sub** — low-latency real-time fan-out within the real-time plane

### Infrastructure
- **AWS EKS (Kubernetes)** — primary cloud platform
- **Istio / Linkerd** — service mesh, mTLS between services
- **Kong / Envoy** — API gateway + BFF layer
- **AWS WAF / Cloudflare** — DDoS protection, bot detection
- **Terraform** IaC + **ArgoCD** GitOps + **GitHub Actions** CI/CD

### Observability
- **Prometheus + Grafana** — metrics
- **Loki / ELK** — logs
- **Tempo / Jaeger** — distributed tracing
- **Sentry** — error tracking

---

## Microservices Reference

30 services defined in the blueprint (§5). Key services and their data stores:

| Service | Language | Data store(s) |
|---|---|---|
| API Gateway / BFF | Kong/Envoy + thin BFF | — |
| Identity & Auth | Spring Boot + Keycloak | PostgreSQL |
| Wallet / Token Ledger | Spring Boot | PostgreSQL (double-entry ledger) |
| Payments | Spring Boot | PostgreSQL |
| Chat | Go | Redis + ScyllaDB |
| Presence | Go | Redis |
| Streaming Orchestration | Go | Redis + PostgreSQL |
| Catalog / Discovery | Spring Boot + Search | OpenSearch + Redis |
| Moderation | Python + Go orchestration | PostgreSQL + S3 |
| Tipping / Gifts | Go / Spring Boot | PostgreSQL + Kafka |
| Private Show Billing | Go | Redis + PostgreSQL |

---

## Streaming Delivery Strategy

**Critical for cost at scale.** Never serve large rooms purely via WebRTC — it is expensive.

- Start viewers on **WebRTC via SFU** (low latency, interactive)
- When a room exceeds ~1,000–2,000 viewers, promote to **LL-HLS via CDN** for the crowd
- Keep tippers and private-show participants on WebRTC
- Private/1-on-1 and cam-to-cam always stay on WebRTC

WebRTC egress costs must be monitored per room. The promotion threshold may need tuning based on actual CDN vs. SFU unit economics.

---

## Security Requirements

- **mTLS** between all internal services (enforced by service mesh)
- **JWT with short expiry** at the edge; refresh tokens stored server-side
- **Signed, expiring URLs** for all media assets — never expose raw S3 URLs
- **Per-segment auth** for HLS to deter stream ripping
- **Forensic watermarking** on video streams (embed viewer identity)
- PII and KYC documents encrypted at rest via KMS; access audited
- WAF must be in the hot path; rate limiting at the gateway

---

## Money / Consistency Rules

- Wallet ledger: **double-entry, idempotent operations only**
- Use **Kafka transactional outbox pattern** for tip/debit events — exactly-once semantics
- Every tip transaction must emit to Kafka before the on-screen animation is confirmed
- Private-show per-minute billing meters in Redis (low latency), reconciled against PostgreSQL ledger on session end
- Chargebacks trigger automatic broadcaster holds; implement a chargeback threshold before payout freeze

---

## Compliance Checklist (must be built as services, not afterthoughts)

- [ ] **18 USC §2257** — immutable, auditable performer record store
- [ ] **GDPR / CCPA** — DSAR and right-to-erasure workflows
- [ ] **DMCA takedown pipeline** — automated + human workflow
- [ ] **CSAM detection + NCMEC reporting** — integrated in moderation service
- [ ] **Geo-blocking** — enforced at edge AND per-broadcaster preferences
- [ ] **Visa VIRP / Mastercard adult program** — model documentation, uploaded-content controls, takedown SLA

---

## Delivery Phases

| Phase | Months | Focus |
|---|---|---|
| 0 | 0–1.5 | Legal, merchant account (long lead time — start immediately), KYC vendor, IaC, CI/CD |
| 1 (MVP) | 2–5 | Auth, broadcaster KYC, catalog, managed streaming (IVS/LiveKit Cloud), chat, wallet, payments, tipping, moderation, admin |
| 2 | 5–8 | Private shows, payouts, VOD, fan-club, gamification, affiliate |
| 3 | 8–12 | Self-hosted SFU, WebRTC→LL-HLS promotion, GPU transcode, multi-CDN, ScyllaDB, ClickHouse, multi-region |
| 4 | 12+ | Mobile native apps, interactive device integration, crypto payouts, ML recommendations |

---

## Performance-Critical Paths

When touching these areas, profile and benchmark before merging:

1. **Chat fan-out** — a 10k-viewer room means one tip event fans out to 10k WebSocket connections. Shard by room ID; never broadcast from a single goroutine.
2. **Presence heartbeats** — Redis TTL-based, designed for high write frequency. Avoid per-update DB writes.
3. **Thumbnail generation** — runs every N seconds per active room; must not block the streaming pipeline. Run as a separate worker consuming from the media egress.
4. **Catalog / discovery grid** — served from cache (Redis). The backing OpenSearch query should only run on cache miss or TTL expiry.
5. **WebRTC simulcast layers** — always request simulcast from the broadcaster (low/medium/high). SFU forwards the appropriate layer; transcoding is a fallback, not the default path.
6. **Wallet debits** — must be serialized per-user (avoid concurrent double-spends). Use PostgreSQL `SELECT FOR UPDATE` or an idempotency key + unique constraint approach.

---

## What to Buy, Not Build

Do not implement these from scratch:

- KYC / age verification → vendor (Veriff / Yoti / Persona)
- High-risk payment processing → CCBill / Epoch / SegPay
- SFU / WebRTC infrastructure (Phase 1) → Amazon IVS or LiveKit Cloud
- Content moderation AI → Hive AI / AWS Rekognition
- CSAM detection → NCMEC PhotoDNA / AWS Rekognition
- Push notifications → FCM (Android) / APNs (iOS) via backend
- Email → SendGrid / AWS SES
- Feature flags → Unleash or LaunchDarkly

---

## Code Conventions

### Go services (real-time plane)
- Prefer `context.Context` propagation everywhere for cancellation and tracing
- Use `errgroup` for concurrent goroutine lifecycle management
- No global state; inject dependencies through constructors
- Structured logging with `slog` or `zerolog`; always include `room_id`, `user_id` in log context

### Java/Spring Boot services (application plane)
- Spring Data JPA for PostgreSQL; Flyway for migrations
- Outbox pattern for all Kafka publishes (transactional consistency)
- `@Transactional` with explicit isolation levels on wallet operations
- Never expose internal IDs in REST responses; use UUIDs

### Python/FastAPI services (ML/moderation)
- Async handlers with `asyncio`; avoid blocking I/O in request handlers
- Pydantic models for all request/response schemas
- Frame sampling workers are separate processes from the API; communicate via Kafka

### General
- All services expose `/health` (liveness) and `/ready` (readiness) endpoints
- All services emit `request_duration_seconds` histogram to Prometheus
- All inter-service calls include a trace parent header (OpenTelemetry)
- Never log PII, KYC document contents, or card data
