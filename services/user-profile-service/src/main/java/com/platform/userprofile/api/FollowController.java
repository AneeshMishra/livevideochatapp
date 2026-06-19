package com.platform.userprofile.api;

import com.platform.userprofile.api.dto.FollowResponse;
import com.platform.userprofile.api.dto.PageResponse;
import com.platform.userprofile.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles/me/follows")
@RequiredArgsConstructor
@Tag(name = "Follows", description = "Follow and unfollow broadcasters")
@SecurityRequirement(name = "bearerAuth")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "Follow a broadcaster")
    @PostMapping("/{broadcasterId}")
    @ResponseStatus(HttpStatus.CREATED)
    public FollowResponse follow(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID broadcasterId
    ) {
        return FollowResponse.from(followService.follow(UUID.fromString(userId), broadcasterId));
    }

    @Operation(summary = "Unfollow a broadcaster")
    @DeleteMapping("/{broadcasterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID broadcasterId
    ) {
        followService.unfollow(UUID.fromString(userId), broadcasterId);
    }

    @Operation(summary = "Check if the current user follows a broadcaster")
    @GetMapping("/{broadcasterId}")
    public FollowStatusResponse isFollowing(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID broadcasterId
    ) {
        return new FollowStatusResponse(followService.isFollowing(UUID.fromString(userId), broadcasterId));
    }

    @Operation(summary = "List all broadcasters the current user follows (paginated)")
    @GetMapping
    public PageResponse<FollowResponse> listFollowing(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(
                followService.listFollowing(UUID.fromString(userId), PageRequest.of(page, Math.min(size, 100))),
                FollowResponse::from
        );
    }

    @Operation(summary = "Get follower count for any broadcaster")
    @GetMapping("/count/{broadcasterId}")
    public FollowerCountResponse followerCount(@PathVariable UUID broadcasterId) {
        return new FollowerCountResponse(followService.countFollowers(broadcasterId));
    }

    record FollowStatusResponse(boolean following) {}
    record FollowerCountResponse(long count) {}
}
