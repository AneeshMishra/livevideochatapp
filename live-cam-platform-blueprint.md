# Live Streaming Platform — Solution Architecture Blueprint
### (Reference model: superchatlive.com / Stripchat / Chaturbate class of platform)

**Author:** Solution Architecture
**Status:** Design v1.0
**Scope:** End-to-end functional analysis, system design, microservice decomposition, technology stack, and phased delivery plan for a live webcam streaming + real-time interaction + token-economy platform.

---

## 0. Reading this document first: the non-negotiables

Before any code, understand that this platform class lives or dies on three things that are *not* engineering challenges but will dictate your entire architecture:

1. **Age & identity verification (AV/KYC).** Both *viewers* and especially *broadcasters* must be verified as 18+. Jurisdictions like the UK (Online Safety Act), many US states (Louisiana-style age-verification laws), and the EU now mandate this. You cannot launch in major markets without it.
2. **Content moderation.** Real-time AI scanning + human review is a legal requirement, not a feature. You must detect and block CSAM, non-consensual content, and prohibited acts *as they stream*, with audit trails.
3. **Payments are "high-risk."** Visa/Mastercard classify adult content as high-risk (MCC 5967/7841). You will need a specialized high-risk merchant acquirer (e.g., CCBill, Epoch, Verotel, SegPay, Vendo), not Stripe/PayPal. Compliance with the **Visa VIRP / Mastercard adult-content program** is mandatory and requires uploaded-content controls, model documentation, and takedown workflows.

Also mandatory: **18 U.S.C. §2257 record-keeping** (proof every performer is 18+, retained and auditable), **GDPR/CCPA** data handling, and **DMCA/CSAM takedown** processes. Build these as core services, not afterthoughts.

> The rest of this document assumes these are in-scope and treats them as first-class subsystems.

---

## 1. Functional Analysis

Everything the reference platform does, grouped by domain.

### 1.1 Discovery & Browsing (public, often pre-login)
- Live grid of online broadcasters with auto-updating thumbnails (periodic snapshots from the live stream).
- Real-time **online/offline presence** and **live viewer counts** per room.
- Categories & tags (region, language, attributes, show type).
- Search and filters (sort by popularity, newest, region, language).
- Featured / promoted / trending rails.
- Preview-on-hover (short looping clip or rotating thumbnails).

### 1.2 Live Streaming Room (the core)
- One-to-many live video (broadcaster → many viewers), low latency.
- Adaptive bitrate (viewer gets a quality matching their bandwidth).
- Public chat with the broadcaster and other viewers.
- **Tipping** with on-screen animations/effects when tips land.
- **Tip menus** (named actions priced in tokens).
- **Goal bars / countdowns** (collective tip goals).
- Interactive device integration (e.g., Lovense-style toy control reacting to tips — common in this category).
- Show modes: **public**, **private 1-on-1** (per-minute billed), **group/ticketed shows**, **spy mode**, **cam-to-cam**.
- Emotes, stickers, animated gifts.
- Moderators (room mods appointed by the broadcaster) with mute/ban powers.
- Fullscreen, theatre mode, picture-in-picture, multi-room view.

### 1.3 Token / Virtual Currency Economy
- Users **buy tokens** (the in-app currency) with real money.
- Tokens spent on tips, private shows, content unlocks, gifts.
- Wallet balance, transaction ledger, spend history.
- Broadcasters **earn tokens**, convertible to a payout in fiat/crypto.
- Promotions, bonus token packs, referral credits.

### 1.4 Broadcaster (Model/Studio) Side
- Broadcaster onboarding + **KYC/2257 document upload**.
- Studio/agency support (one account managing many models, revenue splits).
- Go-live: via browser (WebRTC) **or** OBS/external encoder (RTMP/SRT ingest).
- Real-time dashboard: viewer count, earnings, tipper leaderboard.
- Stream settings: title, tags, category, price for private, tip menu config.
- Earnings dashboard, payout requests, payout method management.
- Content store: sell recorded videos, photo sets; fan club / subscriptions.
- Block/ban list, geo-blocking (hide from chosen regions/countries).
- Schedule, away mode, notifications to followers when going live.

### 1.5 Viewer (User) Account
- Registration / login (email, social, anonymous browsing tier).
- Profile, avatar, bio.
- Follow/favorite broadcasters; "online now" notifications.
- Purchase history, token balance, active subscriptions.
- Direct/private messaging with broadcasters.
- Settings, blocklist, content preferences, language.

### 1.6 Engagement & Gamification
- Tipper rankings / leaderboards (daily/weekly/all-time).
- Broadcaster rankings & contests.
- Badges, levels, loyalty tiers, streaks.
- Notifications (in-app + push + email): favorite is live, replies, promos.

### 1.7 Content (VOD & Media)
- Recordings of past shows (where permitted), purchasable clips.
- Photo galleries / albums.
- Fan-club / subscription gated content.
- CDN-delivered media with signed, expiring URLs.

### 1.8 Trust, Safety & Compliance (cross-cutting)
- Age/identity verification for users and broadcasters.
- Real-time + asynchronous AI content moderation; human review queue.
- Report/flag flows; DMCA & CSAM takedown pipeline.
- 2257 record store (immutable, auditable).
- Geo-restriction / geo-blocking engine.
- Fraud detection (payment fraud, chargeback management, bot detection).
- Full audit logging.

### 1.9 Monetization & Growth
- Affiliate / referral program (revenue share, tracking links, postbacks).
- Advertising / promoted placement.
- Premium memberships / supporter tiers.
- Coupons & campaigns.

### 1.10 Admin / Back-office
- Operations console (users, broadcasters, content, finance, disputes).
- Moderation console.
- Finance: reconciliation, chargebacks, payouts, tax docs.
- Analytics & BI dashboards.
- Feature flags, CMS for static content, support/ticketing.

---

## 2. High-Level System Architecture

```
                                   ┌─────────────────────────────────┐
                                   │            Clients              │
                                   │  Web (React) · iOS · Android     │
                                   │  Broadcaster web / OBS encoder   │
                                   └───────────────┬─────────────────┘
                                                   │ HTTPS / WSS
                                   ┌───────────────▼─────────────────┐
                                   │   CDN (static, HLS, thumbnails)  │
                                   └───────────────┬─────────────────┘
                                                   │
                          ┌────────────────────────▼────────────────────────┐
                          │     Edge / API Gateway + WAF + Rate limiting     │
                          │     (AuthN/Z, routing, request shaping)          │
                          └───┬───────────────┬───────────────┬─────────────┘
                              │               │               │
            ┌─────────────────▼──┐   ┌────────▼─────────┐   ┌─▼──────────────────────┐
            │  Synchronous APIs   │   │  Real-time plane │   │   Media / Streaming      │
            │  (REST/gRPC over    │   │  (WebSocket /    │   │   plane                  │
            │   service mesh)     │   │   pub-sub)       │   │                          │
            └──────────┬──────────┘   └───────┬──────────┘   └──────────┬──────────────┘
                       │                       │                         │
   ┌───────────────────┼───────────────────────┼─────────────────────────┼───────────────┐
   │ Identity/Auth     │ Chat                   │ WebRTC SFU media servers │ Ingest (RTMP/  │
   │ User Profile      │ Presence               │ Transcoder (ABR)         │ SRT/WebRTC)    │
   │ Broadcaster       │ Tip/Event stream       │ Recorder → VOD           │ Thumbnailer    │
   │ Wallet/Tokens     │ Notifications fan-out   │ Egress → HLS/LL-HLS+CDN  │                │
   │ Payments/Payouts  │                        │                          │                │
   │ Catalog/Discovery │                        │                          │                │
   │ Moderation        │                        │                          │                │
   │ AgeVerify/KYC     │                        │                          │                │
   │ Media/VOD         │                        │                          │                │
   │ Gamification      │                        │                          │                │
   │ Affiliate         │                        │                          │                │
   │ Admin/Back-office │                        │                          │                │
   └───────────────────┴────────────────────────┴─────────────────────────┴───────────────┘
                       │                       │                         │
        ┌──────────────▼───────────────────────▼─────────────────────────▼──────────────┐
        │  Event Backbone (Kafka)  ·  Redis (cache/presence/pub-sub)  ·  Service Mesh     │
        └──────────────┬───────────────────────────────────────────────────────────────┘
                       │
        ┌──────────────▼──────────────────────────────────────────────────────────────┐
        │  Data stores: PostgreSQL (per-service) · Redis · Elasticsearch/OpenSearch ·   │
        │  Object storage (S3) · ClickHouse/Snowflake (analytics) · Vault (secrets)     │
        └───────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Three planes, deliberately separated

The single most important design decision: **separate the media plane from the application plane from the real-time messaging plane.** They have completely different scaling, latency, and cost profiles.

| Plane | Concern | Scales on | Latency target |
|---|---|---|---|
| **Application** | Accounts, wallet, catalog, payments | Request volume | 50–300 ms |
| **Real-time** | Chat, presence, tips, events | Concurrent connections | < 200 ms |
| **Media** | Video ingest, transcode, deliver | Bandwidth (Gbps) & sessions | Glass-to-glass < 1–3 s |

---

## 3. The Media/Streaming Pipeline (the part most teams get wrong)

You need a **hybrid delivery model**, because no single protocol is optimal for everything.

### 3.1 Ingest (broadcaster → platform)
- **Browser broadcasters:** WebRTC (getUserMedia → WebRTC publish). Lowest friction, sub-second latency.
- **Pro broadcasters / OBS:** **RTMP** or **SRT** ingest endpoint. SRT preferred for resilience over lossy networks.

### 3.2 Distribution (platform → viewers) — the key trade-off
| Method | Latency | Scale per room | Use for |
|---|---|---|---|
| **WebRTC via SFU** | ~0.2–0.5 s | hundreds–low thousands | Private/1-on-1, cam2cam, small interactive rooms |
| **LL-HLS / LL-DASH via CDN** | ~2–5 s | effectively unlimited | Large public rooms (thousands+ viewers) |

**Strategy:** Start every viewer on WebRTC for interactivity. When a room exceeds a threshold (e.g., 1–2k viewers), transcode the broadcaster's WebRTC ingest into **LL-HLS**, push to CDN, and serve the "crowd" over HLS while keeping tippers/private on WebRTC. This is how the big platforms keep costs sane — WebRTC egress is expensive; CDN HLS is cheap at scale.

### 3.3 SFU choice (do NOT build your own)
- **LiveKit** (Go, open-source, Kubernetes-native, has cloud option) — recommended default.
- **mediasoup** (Node/C++, very flexible, you write more orchestration).
- **Janus** (C, battle-tested, more ops overhead).
- Managed alternatives if you want speed-to-market: Agora, Twilio (sunsetting), Amazon IVS (real-time + low-latency channels), Cloudflare Stream/Calls.

> Pragmatic recommendation: **Amazon IVS** or **LiveKit Cloud** for v1 to ship fast, then bring SFU in-house once unit economics demand it.

### 3.4 Transcoding & ABR
- Generate multiple renditions (e.g., 240p/480p/720p/1080p) for adaptive bitrate.
- FFmpeg-based transcode workers, GPU-accelerated (NVENC) for cost efficiency at scale.
- Simulcast on the WebRTC side so the SFU forwards the right layer without transcoding when possible.

### 3.5 Recording, thumbnails, VOD
- **Recorder** taps the SFU/egress, writes to object storage, hands to VOD pipeline.
- **Thumbnailer** periodically grabs frames from each live room for the discovery grid (and feeds the moderation pipeline — two birds).
- VOD packaged to HLS, served via CDN with **signed expiring URLs**.

### 3.6 Edge & CDN
- Multi-CDN (CloudFront + a second like Fastly/BunnyCDN) for video; geo-routing; token-authenticated segment URLs.
- TURN servers (coturn) geographically distributed for WebRTC NAT traversal.

---

## 4. The Real-Time Plane

High-concurrency, mostly-stateful, sticky connections. Keep it independent of the request/response services.

- **WebSocket gateway** (horizontally scaled, sticky or stateless-with-shared-state via Redis).
- **Chat service** — message validation, rate-limit, profanity/spam filter, room fan-out via **Redis Pub/Sub** or **NATS**; persist to a fast store.
- **Presence service** — who's online, per-room viewer counts; Redis with TTL heartbeats.
- **Tip/Event stream** — tips, gifts, goal updates, "model went private" — all flow as events; drive on-screen animations and trigger wallet debits via the event backbone (Kafka) for durability.
- Backpressure & fan-out: a 10k-viewer room means one tip event fans out to 10k sockets — design the fan-out tier explicitly (sharded by room).

---

## 5. Microservices Decomposition

Each service owns its data (database-per-service). Communication: **gRPC/REST** for sync, **Kafka** for async/events.

| # | Service | Responsibility | Suggested stack | Data store |
|---|---|---|---|---|
| 1 | **API Gateway / BFF** | Edge routing, auth token validation, rate-limit, request shaping; separate BFFs for web/mobile | Kong/Envoy + a thin Spring/Node BFF | — |
| 2 | **Identity & Auth** | Registration, login, OAuth/social, sessions, JWT, MFA, RBAC | Keycloak / Spring Boot + Spring Security | PostgreSQL |
| 3 | **User Profile** | Viewer profiles, preferences, follows, blocklist, settings | Spring Boot | PostgreSQL |
| 4 | **Broadcaster Service** | Model/studio profiles, stream config, tip-menu, schedules, geo-block rules, revenue splits | Spring Boot | PostgreSQL |
| 5 | **Age Verification & KYC** | ID checks for users + 2257 docs for broadcasters; integrates verification vendors; immutable record store | Go/Spring Boot + vendor APIs | PostgreSQL + encrypted object store |
| 6 | **Catalog / Discovery** | Live grid, categories, tags, search, filters, trending; consumes presence + thumbnails | Go/Spring Boot + search engine | Elasticsearch/OpenSearch + Redis |
| 7 | **Streaming Orchestration** | Allocates SFU/ingest, manages room lifecycle, public→HLS promotion, capacity | Go | Redis + PostgreSQL |
| 8 | **Media Ingest** | RTMP/SRT/WebRTC publish endpoints | LiveKit/IVS/nginx-rtmp + Go control plane | — |
| 9 | **SFU / Media Server** | WebRTC forwarding, simulcast | LiveKit / mediasoup | in-memory |
| 10 | **Transcoder** | ABR renditions, WebRTC→HLS | FFmpeg workers (GPU) | object storage |
| 11 | **Recorder / VOD** | Capture, package, manage recordings & clips | Go + FFmpeg | S3 + PostgreSQL |
| 12 | **Thumbnailer** | Periodic frame grabs for grid + moderation feed | Go + FFmpeg | S3/Redis |
| 13 | **Chat** | Real-time messaging, mod actions, history | Go (high-concurrency) | Redis + Cassandra/ScyllaDB |
| 14 | **Presence** | Online state, viewer counts | Go | Redis |
| 15 | **Wallet / Token Ledger** | Token balances, double-entry ledger, debits/credits — *strong consistency, idempotent* | Spring Boot (JVM for transactional rigor) | PostgreSQL (ledger) |
| 16 | **Payments (top-up)** | Token purchase via high-risk acquirers (CCBill/Epoch/SegPay/Verotel), 3DS, fraud, chargebacks | Spring Boot | PostgreSQL |
| 17 | **Payouts** | Model earnings, payout requests, methods (bank/crypto/Paxum), tax docs | Spring Boot | PostgreSQL |
| 18 | **Tipping / Gifts** | Tip transactions, goals, tip-menu execution; orchestrates wallet + event emit | Go/Spring Boot | PostgreSQL + Kafka |
| 19 | **Private Show Billing** | Per-minute metering for private/group shows, session reconciliation | Go | Redis + PostgreSQL |
| 20 | **Moderation** | AI scanning (image/video/text), human review queue, takedowns, audit | Python (ML) + Go orchestration | PostgreSQL + S3 |
| 21 | **Notification** | Push (FCM/APNs), email, in-app; "favorite is live" fan-out | Go/Spring Boot | Redis + PostgreSQL |
| 22 | **Gamification** | Leaderboards, badges, levels, contests | Go | Redis (sorted sets) + PostgreSQL |
| 23 | **Affiliate / Referral** | Tracking links, attribution, rev-share, postbacks | Spring Boot | PostgreSQL |
| 24 | **Content Store** | Paid clips, photo sets, fan-club subscriptions | Spring Boot | PostgreSQL + S3 |
| 25 | **Search Indexer** | Consumes events, keeps Elasticsearch fresh | Go | Elasticsearch |
| 26 | **Analytics / Events** | Clickstream, BI, real-time metrics | Kafka → ClickHouse/Snowflake | ClickHouse |
| 27 | **Admin / Back-office** | Ops, finance, moderation, support consoles | Spring Boot + React admin | PostgreSQL |
| 28 | **Geo / Compliance** | Geo-blocking, jurisdiction rules, consent, DMCA/CSAM workflow | Go/Spring Boot | PostgreSQL |
| 29 | **Media Asset / DRM** | Signed URLs, token-auth segments, watermarking | Go | — |
| 30 | **Feature Flags / Config** | Runtime config, experiments | Unleash/LaunchDarkly | — |

> You do **not** build all 30 at once. See the phased plan in §8. Several (5, 16, 17, 20) lean heavily on **third-party vendors** — buy, don't build.

---

## 6. Recommended Technology Stack

This is biased toward your existing strengths (Java/Spring Boot, AWS EKS) while using the right tool where the JVM isn't ideal (high-concurrency real-time → Go; ML → Python).

**Frontend**
- Web: **React + TypeScript**, Next.js (SSR for SEO on discovery pages), Tailwind.
- Mobile: **React Native** or native (Swift/Kotlin) — native recommended for camera/WebRTC quality.
- Streaming clients: WebRTC (browser native), `livekit-client` SDK, HLS.js for HLS playback.

**Backend services**
- **Java 21 + Spring Boot** for transactional/business services (wallet, payments, payouts, catalog, admin).
- **Go** for real-time and high-throughput services (chat, presence, tipping, streaming orchestration, gateways).
- **Python (FastAPI)** for ML/moderation services.

**Media**
- **LiveKit** (self-host on K8s) or **Amazon IVS** (managed) for SFU/real-time.
- **FFmpeg** (+ NVENC GPU) for transcode/record.
- **coturn** for TURN/STUN.
- nginx-rtmp / SRT for ingest.

**Data**
- **PostgreSQL** — primary OLTP, per service.
- **Redis** — cache, presence, pub/sub, leaderboards, rate-limiting.
- **ScyllaDB / Cassandra** — chat history (high write throughput).
- **Elasticsearch/OpenSearch** — discovery & search.
- **ClickHouse** (or Snowflake) — analytics/BI.
- **S3** (+ CloudFront/Fastly CDN) — media & static.
- **HashiCorp Vault** — secrets; KMS for encryption keys.

**Messaging / eventing**
- **Apache Kafka** (or AWS MSK) — durable event backbone.
- **NATS** or Redis Pub/Sub — low-latency real-time fan-out.

**Platform / infra**
- **Kubernetes (AWS EKS)** — you already know this.
- **Istio/Linkerd** service mesh — mTLS, traffic management.
- **Kong/Envoy** API gateway + **AWS WAF/Cloudflare** for DDoS/WAF.
- **Terraform** IaC, **ArgoCD** GitOps, **GitHub Actions / GitLab CI** pipelines.
- **GPU node groups** for transcoding.

**Observability**
- **Prometheus + Grafana** (metrics), **Loki/ELK** (logs), **Tempo/Jaeger** (tracing), **Sentry** (errors).

**Third-party (buy, don't build)**
- Age/KYC: Yoti, Veriff, Jumio, Persona, AU10TIX.
- Payments (high-risk): CCBill, Epoch, SegPay, Verotel, Vendo. Payouts: Paxum, crypto rails, bank wire.
- Moderation AI: Hive AI, AWS Rekognition (content moderation), Amazon/Google CSAM tooling + **NCMEC reporting integration**.
- Push/email: FCM/APNs, SendGrid/SES.

---

## 7. Cross-Cutting Concerns

**Security**
- mTLS between services; JWT + short-lived tokens at the edge; OAuth2/OIDC.
- PCI-DSS scope minimized by tokenizing through the payment vendor (you should never touch raw card data).
- Encrypt PII and 2257/KYC docs at rest (KMS), strict access controls + audit on every read.
- Signed, expiring URLs for all media; per-segment auth for HLS to deter ripping; forensic watermarking.
- WAF, DDoS protection, bot detection (streaming platforms are heavily targeted by scrapers/credential-stuffing).

**Consistency & money**
- Wallet uses a **double-entry ledger**, idempotent operations, and exactly-once semantics on token debits (use Kafka transactional outbox + idempotency keys). Tips must never double-charge or vanish.
- Per-minute private-show billing meters in real time but reconciles against the ledger.

**Scalability**
- Stateless app services autoscale on CPU/RPS.
- Real-time services scale on connection count; shard rooms.
- Media scales on bandwidth — this is your dominant cost; the WebRTC→HLS promotion strategy (§3.2) is central to unit economics.

**Compliance built-in**
- Geo-blocking enforced at edge + per-broadcaster rules.
- Moderation in the hot path for live (sampled frames every N seconds → AI → auto-cut + human escalation).
- Immutable audit logs for finance, moderation, and access to sensitive records.
- DSAR/right-to-erasure workflows for GDPR.

---

## 8. Phased Delivery Plan

**Phase 0 — Foundations & legal (weeks 0–6)**
Company/legal setup, high-risk merchant account application (long lead time — start now), KYC/AV vendor selection, 2257 process, IaC + EKS + CI/CD skeleton, observability baseline.

**Phase 1 — MVP, single-region (months 2–5)**
Auth, User Profile, Broadcaster onboarding + KYC, Catalog/Discovery, **managed streaming (IVS/LiveKit Cloud)**, Chat, Presence, Wallet, Payments (token top-up), basic Tipping, Moderation (vendor AI + simple review queue), Notification, Admin console. Goal: a public room where a verified model streams, viewers chat and tip, money flows in. Use managed media to avoid running SFUs yet.

**Phase 2 — Monetization depth (months 5–8)**
Private/group shows + per-minute billing, Payouts, Content store/fan-club, Gamification/leaderboards, VOD/recordings, richer tip menus & goals, push notifications, affiliate program.

**Phase 3 — Scale & cost optimization (months 8–12)**
Bring SFU in-house (LiveKit on K8s), WebRTC→LL-HLS promotion pipeline, GPU transcode fleet, multi-CDN, ScyllaDB for chat, ClickHouse analytics, multi-region, advanced fraud/bot defense, A/B testing.

**Phase 4 — Hardening & expansion**
Interactive device integrations, mobile apps, additional payment/payout rails (incl. crypto), expanded geo/jurisdiction compliance, ML-driven recommendations and moderation tuning.

---

## 9. Key Architectural Decisions (ADR summary)

1. **Polyglot by workload:** JVM for money/business logic, Go for real-time/throughput, Python for ML. Don't force one language.
2. **Buy compliance & payments:** vendors for KYC, high-risk payments, and moderation AI — these are existential and you won't out-build specialists.
3. **Hybrid streaming (WebRTC + LL-HLS):** interactivity where it matters, CDN scale where it's cheap.
4. **Managed media first, self-host later:** ship on IVS/LiveKit Cloud; in-source the SFU only when scale justifies the ops burden.
5. **Database-per-service + Kafka event backbone:** loose coupling, independent scaling, durable audit trail.
6. **Wallet as a strongly-consistent double-entry ledger:** money correctness is non-negotiable.
7. **Three separated planes:** application / real-time / media scale and fail independently.

---

## 10. Rough Team & Effort Shape

- Platform/DevOps (2–3), Backend JVM (3–4), Backend Go/real-time (2–3), Media/streaming specialist (1–2), Frontend web (2–3), Mobile (2), ML/Trust & Safety (1–2), QA (2), plus PM, designer, and a compliance/legal lead.
- MVP realistically 4–6 months with a focused team using managed media.

---

*End of blueprint v1.0. This document covers functional scope, architecture, microservice decomposition, stack, and delivery sequencing. Logical data models, per-service API contracts, and sequence diagrams (e.g., "buy tokens → tip → wallet debit → on-screen event") are the natural next deliverables.*
