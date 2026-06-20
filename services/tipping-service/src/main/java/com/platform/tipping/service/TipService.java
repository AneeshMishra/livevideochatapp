package com.platform.tipping.service;

import com.platform.tipping.api.dto.LeaderboardEntry;
import com.platform.tipping.client.WalletClient;
import com.platform.tipping.domain.GoalStatus;
import com.platform.tipping.domain.Tip;
import com.platform.tipping.domain.TipGoal;
import com.platform.tipping.domain.TipMenuItem;
import com.platform.tipping.domain.TipStatus;
import com.platform.tipping.event.TipEvent;
import com.platform.tipping.event.TipEventPublisher;
import com.platform.tipping.exception.InsufficientTokensException;
import com.platform.tipping.exception.MenuItemNotFoundException;
import com.platform.tipping.exception.TipNotFoundException;
import com.platform.tipping.repository.TipGoalRepository;
import com.platform.tipping.repository.TipMenuItemRepository;
import com.platform.tipping.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TipService {

    private final TipRepository tipRepo;
    private final TipMenuItemRepository menuItemRepo;
    private final TipGoalRepository goalRepo;
    private final WalletClient walletClient;
    private final TipEventPublisher eventPublisher;

    /**
     * Sends a tip from a viewer to a broadcaster in a room.
     *
     * Flow:
     *  1. Idempotency check — return existing tip if key already used.
     *  2. Validate tip-menu item price if tipMenuItemId is provided.
     *  3. Persist PENDING tip (obtain a UUID to pass as wallet referenceId).
     *  4. Call wallet-service transfer (pessimistic — holds DB lock during HTTP call;
     *     acceptable for Phase 1. Phase 3: move to async Kafka outbox).
     *  5. On success: mark COMPLETED, update goal progress, publish TipReceived event.
     *  6. On failure: mark FAILED, rethrow so client gets the correct error.
     */
    public Tip sendTip(UUID senderId, UUID recipientId, UUID roomId, long tokenAmount,
                       String message, UUID tipMenuItemId, String idempotencyKey,
                       String senderDisplayName) {

        // 1. Idempotency — safe to call twice with the same key
        var existing = tipRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate tip ignored idempotencyKey={} status={}", idempotencyKey, existing.get().getStatus());
            return existing.get();
        }

        // 2. Menu item validation
        String menuItemTitle = null;
        if (tipMenuItemId != null) {
            TipMenuItem item = menuItemRepo.findById(tipMenuItemId)
                    .orElseThrow(() -> new MenuItemNotFoundException(tipMenuItemId));

            if (!item.isActive()) {
                throw new IllegalArgumentException("Tip menu item is no longer active: " + tipMenuItemId);
            }
            if (!item.getBroadcasterId().equals(recipientId)) {
                throw new IllegalArgumentException("Tip menu item does not belong to this broadcaster");
            }
            if (item.getTokenPrice() != tokenAmount) {
                throw new IllegalArgumentException(
                        "Token amount " + tokenAmount + " does not match menu item price " + item.getTokenPrice());
            }
            menuItemTitle = item.getTitle();
        }

        // 3. Persist PENDING tip — get its UUID for the wallet referenceId
        Tip tip = Tip.create(senderId, recipientId, roomId, tokenAmount, message,
                              tipMenuItemId, idempotencyKey, senderDisplayName);
        tip = tipRepo.save(tip);

        // 4. Wallet transfer (blocks until wallet confirms)
        try {
            walletClient.transfer(senderId, recipientId, tokenAmount, tip.getId(), idempotencyKey);

            tip.setStatus(TipStatus.COMPLETED);
            tip.setCompletedAt(Instant.now());
            tip = tipRepo.save(tip);

            log.info("Tip completed tipId={} senderId={} recipientId={} tokens={}",
                    tip.getId(), senderId, recipientId, tokenAmount);

        } catch (InsufficientTokensException ex) {
            tip.setStatus(TipStatus.FAILED);
            tip.setFailureReason("Insufficient tokens");
            tipRepo.save(tip);
            throw ex;  // propagates 422 to client
        } catch (WalletClient.WalletServiceException ex) {
            tip.setStatus(TipStatus.FAILED);
            tip.setFailureReason("Wallet service unavailable");
            tipRepo.save(tip);
            throw ex;  // propagates 503 to client
        }

        // 5a. Update active goal progress (in the same transaction, with lock)
        boolean goalJustCompleted = false;
        TipGoal completedGoal = null;
        var activeGoal = goalRepo.findActiveGoalForUpdate(recipientId);
        if (activeGoal.isPresent()) {
            TipGoal goal = activeGoal.get();
            goalJustCompleted = goal.addProgress(tokenAmount);
            goalRepo.save(goal);
            if (goalJustCompleted) {
                completedGoal = goal;
            }
        }

        // 5b. Publish events (best-effort; wallet transfer already committed)
        final String finalMenuItemTitle = menuItemTitle;
        final Tip finalTip = tip;
        publishTipReceived(finalTip, finalMenuItemTitle, tipMenuItemId);

        if (activeGoal.isPresent()) {
            TipGoal goal = activeGoal.get();
            publishGoalUpdated(goal);
            if (goalJustCompleted) {
                publishGoalCompleted(goal);
            }
        }

        return tip;
    }

    @Transactional(readOnly = true)
    public Tip getTip(UUID tipId, UUID requestingUserId) {
        Tip tip = tipRepo.findById(tipId).orElseThrow(() -> new TipNotFoundException(tipId));
        // Only sender, recipient, or admin can view a tip
        if (!tip.getSenderId().equals(requestingUserId) && !tip.getRecipientId().equals(requestingUserId)) {
            throw new TipNotFoundException(tipId);
        }
        return tip;
    }

    @Transactional(readOnly = true)
    public Page<Tip> getSentTips(UUID senderId, Pageable pageable) {
        return tipRepo.findBySenderIdAndStatusOrderByCreatedAtDesc(senderId, TipStatus.COMPLETED, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Tip> getReceivedTips(UUID recipientId, Pageable pageable) {
        return tipRepo.findByRecipientIdAndStatusOrderByCreatedAtDesc(recipientId, TipStatus.COMPLETED, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Tip> getRoomTips(UUID roomId, Pageable pageable) {
        return tipRepo.findByRoomIdAndStatusOrderByCreatedAtDesc(roomId, TipStatus.COMPLETED, pageable);
    }

    // ── Leaderboards ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getRoomLeaderboard(UUID roomId, int limit) {
        var rows = tipRepo.findTopTippersByRoom(roomId, PageRequest.of(0, limit));
        List<LeaderboardEntry> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            result.add(LeaderboardEntry.fromRow(rows.get(i), i + 1));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getBroadcasterLeaderboard(UUID broadcasterId, int limit) {
        var rows = tipRepo.findTopTippersForBroadcaster(broadcasterId, PageRequest.of(0, limit));
        List<LeaderboardEntry> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            result.add(LeaderboardEntry.fromRow(rows.get(i), i + 1));
        }
        return result;
    }

    // ── private event helpers ──────────────────────────────────────────────────

    private void publishTipReceived(Tip tip, String menuItemTitle, UUID menuItemId) {
        eventPublisher.publish(new TipEvent.TipReceived(
                tip.getId(), tip.getRoomId(),
                tip.getSenderId(), tip.getSenderDisplayName(),
                tip.getRecipientId(), tip.getTokenAmount(),
                tip.getMessage(), menuItemId, menuItemTitle,
                tip.getCompletedAt()));
    }

    private void publishGoalUpdated(TipGoal goal) {
        eventPublisher.publish(new TipEvent.TipGoalUpdated(
                goal.getId(), goal.getRoomId(), goal.getBroadcasterId(),
                goal.getTitle(), goal.getTargetTokens(), goal.getCurrentTokens(),
                goal.progressPercent(), Instant.now()));
    }

    private void publishGoalCompleted(TipGoal goal) {
        eventPublisher.publish(new TipEvent.TipGoalCompleted(
                goal.getId(), goal.getRoomId(), goal.getBroadcasterId(),
                goal.getTitle(), goal.getTargetTokens(), Instant.now()));
    }
}
