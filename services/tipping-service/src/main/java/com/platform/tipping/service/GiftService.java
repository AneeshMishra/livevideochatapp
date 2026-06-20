package com.platform.tipping.service;

import com.platform.tipping.api.dto.LeaderboardEntry;
import com.platform.tipping.client.WalletClient;
import com.platform.tipping.domain.GiftSent;
import com.platform.tipping.domain.GiftType;
import com.platform.tipping.event.TipEvent;
import com.platform.tipping.event.TipEventPublisher;
import com.platform.tipping.exception.InsufficientTokensException;
import com.platform.tipping.repository.GiftSentRepository;
import com.platform.tipping.repository.GiftTypeRepository;
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
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GiftService {

    private final GiftTypeRepository giftTypeRepo;
    private final GiftSentRepository giftSentRepo;
    private final WalletClient walletClient;
    private final TipEventPublisher eventPublisher;

    // ── Gift catalog (read-only) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GiftType> getCatalog() {
        return giftTypeRepo.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public GiftType getGiftType(UUID id) {
        return giftTypeRepo.findById(id)
                .filter(GiftType::isActive)
                .orElseThrow(() -> new NoSuchElementException("Gift type not found: " + id));
    }

    // ── Send gift ─────────────────────────────────────────────────────────────

    /**
     * Sends a virtual gift from a viewer to a broadcaster in a room.
     *
     * Flow:
     *  1. Idempotency — return existing if key already used.
     *  2. Look up gift type and confirm it is active.
     *  3. Persist the gift record.
     *  4. Wallet transfer (token debit from sender, credit to recipient).
     *  5. Publish GIFT_SENT Kafka event for real-time fan-out.
     *
     * Token price is always taken from the gift_type — clients cannot override it.
     */
    public GiftSent sendGift(UUID senderId, String senderDisplayName,
                              UUID recipientId, UUID roomId,
                              UUID giftTypeId, String message, String idempotencyKey) {

        // 1. Idempotency
        var existing = giftSentRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate gift ignored idempotencyKey={}", idempotencyKey);
            return existing.get();
        }

        // 2. Gift type lookup
        GiftType giftType = giftTypeRepo.findById(giftTypeId)
                .filter(GiftType::isActive)
                .orElseThrow(() -> new NoSuchElementException("Gift type not found or inactive: " + giftTypeId));

        // 3. Persist
        GiftSent gift = GiftSent.create(senderId, senderDisplayName, recipientId, roomId,
                                        giftType, message, idempotencyKey);
        gift = giftSentRepo.save(gift);

        // 4. Wallet transfer
        try {
            walletClient.transfer(senderId, recipientId, giftType.getTokenPrice(),
                                  gift.getId(), idempotencyKey);
            log.info("Gift sent giftSentId={} senderId={} recipientId={} tokens={}",
                    gift.getId(), senderId, recipientId, giftType.getTokenPrice());
        } catch (InsufficientTokensException ex) {
            giftSentRepo.delete(gift);  // roll back the persisted record
            throw ex;
        } catch (WalletClient.WalletServiceException ex) {
            giftSentRepo.delete(gift);
            throw ex;
        }

        // 5. Kafka event
        eventPublisher.publish(new TipEvent.GiftSent(
                gift.getId(), roomId, senderId, senderDisplayName,
                recipientId, giftTypeId, giftType.getName(),
                giftType.getAnimationType(), giftType.getTokenPrice(),
                message, Instant.now()));

        return gift;
    }

    // ── Gift history ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<GiftSent> getGiftsByRoom(UUID roomId, Pageable pageable) {
        return giftSentRepo.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<GiftSent> getGiftsReceived(UUID recipientId, Pageable pageable) {
        return giftSentRepo.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<GiftSent> getGiftsSent(UUID senderId, Pageable pageable) {
        return giftSentRepo.findBySenderIdOrderByCreatedAtDesc(senderId, pageable);
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getRoomGiftLeaderboard(UUID roomId, int limit) {
        var rows = giftSentRepo.findTopGiftSendersInRoom(roomId, PageRequest.of(0, limit));
        List<LeaderboardEntry> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            result.add(LeaderboardEntry.fromRow(rows.get(i), i + 1));
        }
        return result;
    }

    // ── ADMIN gift-type management ────────────────────────────────────────────

    public GiftType createGiftType(String name, String slug, String description,
                                    String iconUrl, String animationType,
                                    long tokenPrice, int displayOrder) {
        if (giftTypeRepo.findBySlugAndActiveTrue(slug).isPresent()) {
            throw new IllegalArgumentException("Gift type with slug already exists: " + slug);
        }
        GiftType gt = new GiftType();
        gt.setName(name);
        gt.setSlug(slug);
        gt.setDescription(description);
        gt.setIconUrl(iconUrl);
        gt.setAnimationType(animationType);
        gt.setTokenPrice(tokenPrice);
        gt.setDisplayOrder(displayOrder);
        gt.setActive(true);
        return giftTypeRepo.save(gt);
    }

    public GiftType deactivateGiftType(UUID id) {
        GiftType gt = giftTypeRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Gift type not found: " + id));
        gt.setActive(false);
        return giftTypeRepo.save(gt);
    }
}
