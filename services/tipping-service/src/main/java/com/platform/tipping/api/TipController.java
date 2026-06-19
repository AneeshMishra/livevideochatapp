package com.platform.tipping.api;

import com.platform.tipping.api.dto.SendTipRequest;
import com.platform.tipping.api.dto.TipResponse;
import com.platform.tipping.domain.Tip;
import com.platform.tipping.service.TipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tips")
@RequiredArgsConstructor
public class TipController {

    private final TipService tipService;

    /**
     * Send a tip. Returns 201 immediately when the wallet transfer succeeds
     * and the Kafka event has been dispatched for real-time fan-out.
     * Returns 422 if the viewer's token balance is insufficient.
     */
    @PostMapping
    public ResponseEntity<TipResponse> sendTip(
            @RequestBody @Valid SendTipRequest req,
            Authentication auth) {

        UUID senderId = UUID.fromString(auth.getName());
        Tip tip = tipService.sendTip(
                senderId, req.recipientId(), req.roomId(),
                req.tokenAmount(), req.message(), req.tipMenuItemId(),
                req.idempotencyKey(), req.senderDisplayName());

        return ResponseEntity.status(HttpStatus.CREATED).body(TipResponse.from(tip));
    }

    @GetMapping("/{tipId}")
    public ResponseEntity<TipResponse> getTip(@PathVariable UUID tipId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(TipResponse.from(tipService.getTip(tipId, userId)));
    }

    // Tips the authenticated user has sent
    @GetMapping("/sent")
    public ResponseEntity<Page<TipResponse>> sentTips(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = UUID.fromString(auth.getName());
        Page<TipResponse> result = tipService.getSentTips(userId, PageRequest.of(page, Math.min(size, 100)))
                .map(TipResponse::from);
        return ResponseEntity.ok(result);
    }

    // Tips the authenticated broadcaster has received
    @GetMapping("/received")
    public ResponseEntity<Page<TipResponse>> receivedTips(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = UUID.fromString(auth.getName());
        Page<TipResponse> result = tipService.getReceivedTips(userId, PageRequest.of(page, Math.min(size, 100)))
                .map(TipResponse::from);
        return ResponseEntity.ok(result);
    }

    // All tips in a room (for the live tip feed / leaderboard)
    @GetMapping("/room/{roomId}")
    public ResponseEntity<Page<TipResponse>> roomTips(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<TipResponse> result = tipService.getRoomTips(roomId, PageRequest.of(page, Math.min(size, 100)))
                .map(TipResponse::from);
        return ResponseEntity.ok(result);
    }
}
