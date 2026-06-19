package com.platform.payments.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chargeback_records", indexes = {
    @Index(name = "idx_cb_payment_order_id", columnList = "payment_order_id"),
    @Index(name = "idx_cb_user_id",           columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ChargebackRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_order_id", nullable = false)
    private UUID paymentOrderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_chargeback_id", nullable = false, unique = true, length = 255)
    private String providerChargebackId;

    @Column(name = "chargeback_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal chargebackAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    public static ChargebackRecord of(UUID paymentOrderId, UUID userId,
                                      String providerChargebackId,
                                      BigDecimal chargebackAmount, String currencyCode,
                                      String reason) {
        ChargebackRecord cb = new ChargebackRecord();
        cb.paymentOrderId = paymentOrderId;
        cb.userId = userId;
        cb.providerChargebackId = providerChargebackId;
        cb.chargebackAmount = chargebackAmount;
        cb.currencyCode = currencyCode;
        cb.reason = reason;
        cb.receivedAt = Instant.now();
        return cb;
    }
}
