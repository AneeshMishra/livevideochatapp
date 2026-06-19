package com.platform.tipping.repository;

import com.platform.tipping.domain.Tip;
import com.platform.tipping.domain.TipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TipRepository extends JpaRepository<Tip, UUID> {

    Optional<Tip> findByIdempotencyKey(String idempotencyKey);

    Page<Tip> findBySenderIdAndStatusOrderByCreatedAtDesc(UUID senderId, TipStatus status, Pageable pageable);

    Page<Tip> findByRecipientIdAndStatusOrderByCreatedAtDesc(UUID recipientId, TipStatus status, Pageable pageable);

    Page<Tip> findByRoomIdAndStatusOrderByCreatedAtDesc(UUID roomId, TipStatus status, Pageable pageable);

    // Leaderboard: top tippers in a room
    @Query("""
        SELECT t.senderId, SUM(t.tokenAmount) AS total
        FROM Tip t
        WHERE t.roomId = :roomId AND t.status = 'COMPLETED'
        GROUP BY t.senderId
        ORDER BY total DESC
        """)
    Page<Object[]> findTopTippersByRoom(@Param("roomId") UUID roomId, Pageable pageable);
}
