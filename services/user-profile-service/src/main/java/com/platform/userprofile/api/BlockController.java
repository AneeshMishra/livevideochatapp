package com.platform.userprofile.api;

import com.platform.userprofile.api.dto.BlockRequest;
import com.platform.userprofile.api.dto.BlockResponse;
import com.platform.userprofile.api.dto.PageResponse;
import com.platform.userprofile.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles/me/blocks")
@RequiredArgsConstructor
@Tag(name = "Blocks", description = "Block and unblock users")
@SecurityRequirement(name = "bearerAuth")
public class BlockController {

    private final BlockService blockService;

    @Operation(summary = "Block a user")
    @PostMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    public BlockResponse block(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody(required = false) BlockRequest req
    ) {
        String reason = req != null ? req.reason() : null;
        return BlockResponse.from(blockService.block(UUID.fromString(userId), targetUserId, reason));
    }

    @Operation(summary = "Unblock a user")
    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID targetUserId
    ) {
        blockService.unblock(UUID.fromString(userId), targetUserId);
    }

    @Operation(summary = "List all blocked users (paginated)")
    @GetMapping
    public PageResponse<BlockResponse> listBlocked(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(
                blockService.listBlocked(UUID.fromString(userId), PageRequest.of(page, Math.min(size, 100))),
                BlockResponse::from
        );
    }
}
