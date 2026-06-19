package com.platform.tipping.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tips", indexes = {
    @Index(name = "idx_tip_sender_id",    columnList = "sender_id"),
    @Index(name = "idx_tip_recipient_id", columnList = "recipient_id"),
    @Index(name = "idx_tip_room_id",      columnList = "room_id"),
    @Index(name = "idx_tip_created_at",   columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Tip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "token_amount", nullable = false)
    private long tokenAmount;

    @Column(name = "message", length = 500)
    private String message;

    // Optional: tip was triggered by clicking a menu item
    @Column(name = "tip_menu_item_id")
    private UUID tipMenuItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipStatus status;

    // Idempotency key provided by the client — prevents double-tip on retry
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    // Set on FAILED; never exposed externally in full detail
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    // Display name snapshot — denormalised so the Kafka event doesn't require a profile lookup
    @Column(name = "sender_display_name", length = 100)
    private String senderDisplayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }

    public static Tip create(UUID senderId, UUID recipientId, UUID roomId,
                              long tokenAmount, String message, UUID tipMenuItemId,
                              String idempotencyKey, String senderDisplayName) {
        Tip t = new Tip();
        t.senderId = senderId;
        t.recipientId = recipientId;
        t.roomId = roomId;
        t.tokenAmount = tokenAmount;
        t.message = message;
        t.tipMenuItemId = tipMenuItemId;
        t.status = TipStatus.PENDING;
        t.idempotencyKey = idempotencyKey;
        t.senderDisplayName = senderDisplayName;
        return t;
    }
}
