package com.platform.tipping.repository;

import com.platform.tipping.domain.GoalStatus;
import com.platform.tipping.domain.TipGoal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TipGoalRepository extends JpaRepository<TipGoal, UUID> {

    Optional<TipGoal> findByBroadcasterIdAndStatus(UUID broadcasterId, GoalStatus status);

    List<TipGoal> findByBroadcasterIdOrderByCreatedAtDesc(UUID broadcasterId);

    // Pessimistic lock — used in TipService to safely increment currentTokens
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM TipGoal g WHERE g.broadcasterId = :broadcasterId AND g.status = 'ACTIVE'")
    Optional<TipGoal> findActiveGoalForUpdate(@Param("broadcasterId") UUID broadcasterId);
}
