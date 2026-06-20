package com.platform.tipping.repository;

import com.platform.tipping.domain.GiftSent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GiftSentRepository extends JpaRepository<GiftSent, UUID> {

    Optional<GiftSent> findByIdempotencyKey(String idempotencyKey);

    Page<GiftSent> findByRoomIdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);

    Page<GiftSent> findBySenderIdOrderByCreatedAtDesc(UUID senderId, Pageable pageable);

    Page<GiftSent> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    /** Top senders in a room by total tokens spent on gifts. */
    @Query("""
            SELECT g.senderId AS senderId, g.senderDisplayName AS displayName,
                   SUM(g.tokenAmount) AS totalTokens, COUNT(g.id) AS giftCount
              FROM GiftSent g
             WHERE g.roomId = :roomId
             GROUP BY g.senderId, g.senderDisplayName
             ORDER BY totalTokens DESC
            """)
    List<Object[]> findTopGiftSendersInRoom(@Param("roomId") UUID roomId, Pageable pageable);
}
