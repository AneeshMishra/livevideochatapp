package com.platform.tipping.service;

import com.platform.tipping.domain.GoalStatus;
import com.platform.tipping.domain.TipGoal;
import com.platform.tipping.exception.TipGoalNotFoundException;
import com.platform.tipping.repository.TipGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class TipGoalService {

    private final TipGoalRepository goalRepo;

    public TipGoal createGoal(UUID broadcasterId, UUID roomId, String title, long targetTokens) {
        // Only one active goal per broadcaster at a time
        goalRepo.findByBroadcasterIdAndStatus(broadcasterId, GoalStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Broadcaster already has an active goal: " + existing.getId());
                });

        TipGoal goal = TipGoal.create(broadcasterId, roomId, title, targetTokens);
        return goalRepo.save(goal);
    }

    public TipGoal cancelGoal(UUID goalId, UUID broadcasterId) {
        TipGoal goal = goalRepo.findById(goalId)
                .orElseThrow(() -> new TipGoalNotFoundException(goalId));

        if (!goal.getBroadcasterId().equals(broadcasterId)) {
            throw new TipGoalNotFoundException(goalId);
        }
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new IllegalStateException("Goal is not active: " + goalId);
        }

        goal.setStatus(GoalStatus.CANCELLED);
        return goalRepo.save(goal);
    }

    @Transactional(readOnly = true)
    public Optional<TipGoal> getActiveGoal(UUID broadcasterId) {
        return goalRepo.findByBroadcasterIdAndStatus(broadcasterId, GoalStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public TipGoal getGoal(UUID goalId) {
        return goalRepo.findById(goalId).orElseThrow(() -> new TipGoalNotFoundException(goalId));
    }

    @Transactional(readOnly = true)
    public List<TipGoal> getBroadcasterGoals(UUID broadcasterId) {
        return goalRepo.findByBroadcasterIdOrderByCreatedAtDesc(broadcasterId);
    }
}
