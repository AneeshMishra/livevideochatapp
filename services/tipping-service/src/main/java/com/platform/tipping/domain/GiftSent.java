package com.platform.tipping.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gifts_sent")
@Getter
@Setter
@NoArgsConstructor
public class GiftSent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "gift_type_id", nullable = false)
    private UUID giftTypeId;

    /** Denormalised snapshot — the gift name at send time. */
    @Column(name = "gift_type_name", nullable = false, length = 100)
    private String giftTypeName;

    /** Denormalised — animation type at send time. */
    @Column(name = "animation_type", nullable = false, length = 50)
    private String animationType;

    @Column(name = "token_amount", nullable = false)
    private long tokenAmount;

    @Column(name = "sender_display_name", nullable = false, length = 150)
    private String senderDisplayName;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }

    public static GiftSent create(UUID senderId, String senderDisplayName, UUID recipientId,
                                   UUID roomId, GiftType giftType, String message, String idempotencyKey) {
        GiftSent g = new GiftSent();
        g.senderId            = senderId;
        g.senderDisplayName   = senderDisplayName;
        g.recipientId         = recipientId;
        g.roomId              = roomId;
        g.giftTypeId          = giftType.getId();
        g.giftTypeName        = giftType.getName();
        g.animationType       = giftType.getAnimationType();
        g.tokenAmount         = giftType.getTokenPrice();
        g.message             = message;
        g.idempotencyKey      = idempotencyKey;
        return g;
    }
}
