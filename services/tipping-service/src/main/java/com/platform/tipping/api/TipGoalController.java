package com.platform.tipping.api;

import com.platform.tipping.api.dto.GoalRequest;
import com.platform.tipping.api.dto.GoalResponse;
import com.platform.tipping.service.TipGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tip-goals")
@RequiredArgsConstructor
public class TipGoalController {

    private final TipGoalService goalService;

    // Public — viewers see the broadcaster's current goal in the room
    @GetMapping("/broadcasters/{broadcasterId}/active")
    public ResponseEntity<GoalResponse> getActiveGoal(@PathVariable UUID broadcasterId) {
        return goalService.getActiveGoal(broadcasterId)
                .map(GoalResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{goalId}")
    public ResponseEntity<GoalResponse> getGoal(@PathVariable UUID goalId) {
        return ResponseEntity.ok(GoalResponse.from(goalService.getGoal(goalId)));
    }

    // Broadcaster's full goal history
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('BROADCASTER','ADMIN')")
    public ResponseEntity<List<GoalResponse>> myGoals(Authentication auth) {
        UUID broadcasterId = UUID.fromString(auth.getName());
        List<GoalResponse> goals = goalService.getBroadcasterGoals(broadcasterId).stream()
                .map(GoalResponse::from).toList();
        return ResponseEntity.ok(goals);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BROADCASTER','ADMIN')")
    public ResponseEntity<GoalResponse> createGoal(
            @RequestBody @Valid GoalRequest req,
            Authentication auth) {

        UUID broadcasterId = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(GoalResponse.from(
                goalService.createGoal(broadcasterId, req.roomId(), req.title(), req.targetTokens())));
    }

    @DeleteMapping("/{goalId}")
    @PreAuthorize("hasAnyRole('BROADCASTER','ADMIN')")
    public ResponseEntity<GoalResponse> cancelGoal(@PathVariable UUID goalId, Authentication auth) {
        UUID broadcasterId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(GoalResponse.from(goalService.cancelGoal(goalId, broadcasterId)));
    }
}
