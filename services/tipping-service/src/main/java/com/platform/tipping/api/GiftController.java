package com.platform.tipping.api;

import com.platform.tipping.api.dto.CreateGiftTypeRequest;
import com.platform.tipping.api.dto.GiftSentResponse;
import com.platform.tipping.api.dto.GiftTypeResponse;
import com.platform.tipping.api.dto.LeaderboardEntry;
import com.platform.tipping.api.dto.SendGiftRequest;
import com.platform.tipping.service.GiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gifts")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    // ── Public: gift type catalog ─────────────────────────────────────────────

    @GetMapping("/catalog")
    public ResponseEntity<List<GiftTypeResponse>> getCatalog() {
        List<GiftTypeResponse> items = giftService.getCatalog().stream()
                .map(GiftTypeResponse::from).toList();
        return ResponseEntity.ok(items);
    }

    // ── Authenticated: send a gift ────────────────────────────────────────────

    @PostMapping("/send")
    public ResponseEntity<GiftSentResponse> sendGift(
            @RequestBody @Valid SendGiftRequest req,
            Authentication auth) {

        UUID senderId = UUID.fromString(auth.getName());
        var gift = giftService.sendGift(
                senderId, req.senderDisplayName(),
                req.recipientId(), req.roomId(),
                req.giftTypeId(), req.message(), req.idempotencyKey());

        return ResponseEntity.status(HttpStatus.CREATED).body(GiftSentResponse.from(gift));
    }

    // ── Gift history ──────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}")
    public ResponseEntity<Page<GiftSentResponse>> roomGifts(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(
                giftService.getGiftsByRoom(roomId, PageRequest.of(page, Math.min(size, 100)))
                        .map(GiftSentResponse::from));
    }

    @GetMapping("/received")
    public ResponseEntity<Page<GiftSentResponse>> receivedGifts(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(
                giftService.getGiftsReceived(userId, PageRequest.of(page, Math.min(size, 100)))
                        .map(GiftSentResponse::from));
    }

    @GetMapping("/sent")
    public ResponseEntity<Page<GiftSentResponse>> sentGifts(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(
                giftService.getGiftsSent(userId, PageRequest.of(page, Math.min(size, 100)))
                        .map(GiftSentResponse::from));
    }

    // ── Gift leaderboard ──────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> roomGiftLeaderboard(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(giftService.getRoomGiftLeaderboard(roomId, Math.min(limit, 100)));
    }

    // ── ADMIN: gift type management ───────────────────────────────────────────

    @PostMapping("/admin/catalog")
    public ResponseEntity<GiftTypeResponse> createGiftType(
            @RequestBody @Valid CreateGiftTypeRequest req) {

        var gt = giftService.createGiftType(
                req.name(), req.slug(), req.description(),
                req.iconUrl(), req.animationType(),
                req.tokenPrice(), req.displayOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(GiftTypeResponse.from(gt));
    }

    @DeleteMapping("/admin/catalog/{id}")
    public ResponseEntity<Void> deactivateGiftType(@PathVariable UUID id) {
        giftService.deactivateGiftType(id);
        return ResponseEntity.noContent().build();
    }
}
