package com.platform.payments.event;

import com.platform.payments.domain.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public sealed interface PaymentEvent permits
        PaymentEvent.PaymentCompleted,
        PaymentEvent.PaymentFailed,
        PaymentEvent.ChargebackReceived {

    String eventType();
    UUID userId();
    Instant occurredAt();

    // Published when a provider confirms payment — wallet service consumes to credit tokens
    record PaymentCompleted(
            UUID orderId,
            UUID userId,
            long tokenAmount,
            PaymentProvider provider,
            String idempotencyKey,   // used by wallet service as its own idempotency key
            Instant occurredAt
    ) implements PaymentEvent {
        public String eventType() { return "PAYMENT_COMPLETED"; }
    }

    // Published when a payment is declined so clients can react (e.g., show retry UI)
    record PaymentFailed(
            UUID orderId,
            UUID userId,
            String reason,
            Instant occurredAt
    ) implements PaymentEvent {
        public String eventType() { return "PAYMENT_FAILED"; }
    }

    // Published on chargeback — broadcaster-service can freeze related payouts
    record ChargebackReceived(
            UUID orderId,
            UUID userId,
            BigDecimal chargebackAmount,
            String currencyCode,
            String reason,
            Instant occurredAt
    ) implements PaymentEvent {
        public String eventType() { return "CHARGEBACK_RECEIVED"; }
    }
}
