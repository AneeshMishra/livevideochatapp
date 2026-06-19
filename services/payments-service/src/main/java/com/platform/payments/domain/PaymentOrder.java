package com.platform.payments.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders", indexes = {
    @Index(name = "idx_po_user_id",       columnList = "user_id"),
    @Index(name = "idx_po_status",         columnList = "status"),
    @Index(name = "idx_po_created_at",     columnList = "created_at"),
    @Index(name = "idx_po_provider_order", columnList = "provider, provider_order_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    // Transaction ID assigned by the payment provider (set on webhook confirmation)
    @Column(name = "provider_order_id", length = 255)
    private String providerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "token_amount", nullable = false)
    private long tokenAmount;

    @Column(name = "fiat_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal fiatAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    // Caller-provided dedup key; also used as the wallet idempotency key on credit
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    // URL returned to the client for redirect to payment provider
    @Column(name = "provider_redirect_url", length = 2048)
    private String providerRedirectUrl;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    // Raw webhook payload stored for audit / replay
    @Column(name = "raw_webhook_payload", columnDefinition = "TEXT")
    private String rawWebhookPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }

    public static PaymentOrder create(UUID userId, PaymentProvider provider,
                                      long tokenAmount, BigDecimal fiatAmount,
                                      String currencyCode, String idempotencyKey) {
        PaymentOrder o = new PaymentOrder();
        o.userId = userId;
        o.provider = provider;
        o.status = OrderStatus.PENDING;
        o.tokenAmount = tokenAmount;
        o.fiatAmount = fiatAmount;
        o.currencyCode = currencyCode;
        o.idempotencyKey = idempotencyKey;
        return o;
    }
}
