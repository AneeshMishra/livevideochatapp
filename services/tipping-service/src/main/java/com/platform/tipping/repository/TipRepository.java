package com.platform.tipping.repository;

import com.platform.tipping.domain.Tip;
import com.platform.tipping.domain.TipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TipRepository extends JpaRepository<Tip, UUID> {

    Optional<Tip> findByIdempotencyKey(String idempotencyKey);

    Page<Tip> findBySenderIdAndStatusOrderByCreatedAtDesc(UUID senderId, TipStatus status, Pageable pageable);

    Page<Tip> findByRecipientIdAndStatusOrderByCreatedAtDesc(UUID recipientId, TipStatus status, Pageable pageable);

    Page<Tip> findByRoomIdAndStatusOrderByCreatedAtDesc(UUID roomId, TipStatus status, Pageable pageable);

    /** Top tippers in a specific room, ordered by total tokens spent. */
    @Query("""
        SELECT t.senderId AS senderId, t.senderDisplayName AS displayName,
               SUM(t.tokenAmount) AS totalTokens, COUNT(t.id) AS tipCount
          FROM Tip t
         WHERE t.roomId = :roomId AND t.status = 'COMPLETED'
         GROUP BY t.senderId, t.senderDisplayName
         ORDER BY totalTokens DESC
        """)
    List<Object[]> findTopTippersByRoom(@Param("roomId") UUID roomId, Pageable pageable);

    /** Top tippers for a broadcaster across all rooms, all-time. */
    @Query("""
        SELECT t.senderId AS senderId, t.senderDisplayName AS displayName,
               SUM(t.tokenAmount) AS totalTokens, COUNT(t.id) AS tipCount
          FROM Tip t
         WHERE t.recipientId = :broadcasterId AND t.status = 'COMPLETED'
         GROUP BY t.senderId, t.senderDisplayName
         ORDER BY totalTokens DESC
        """)
    List<Object[]> findTopTippersForBroadcaster(@Param("broadcasterId") UUID broadcasterId, Pageable pageable);
}
