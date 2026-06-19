package com.platform.broadcaster.api;

import com.platform.broadcaster.api.dto.BroadcasterProfileResponse;
import com.platform.broadcaster.domain.Studio;
import com.platform.broadcaster.service.StudioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/studios")
@RequiredArgsConstructor
@Tag(name = "Studios", description = "Agency/studio management and broadcaster roster")
public class StudioController {

    private final StudioService studioService;
    private final BroadcasterMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new studio")
    public Studio create(
        @RequestParam @NotNull UUID ownerId,
        @RequestParam @NotBlank String name,
        @RequestParam(defaultValue = "40") @Min(1) @Max(99) int defaultRevenueSplitPercent
    ) {
        return studioService.create(ownerId, name, defaultRevenueSplitPercent);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDIO_OWNER')")
    @Operation(summary = "Get studio details")
    public Studio getById(@PathVariable UUID id) {
        return studioService.getById(id);
    }

    @GetMapping("/{id}/broadcasters")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDIO_OWNER')")
    @Operation(summary = "List all broadcasters managed by this studio")
    public List<BroadcasterProfileResponse> listBroadcasters(@PathVariable UUID id) {
        return studioService.listBroadcasters(id).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @PutMapping("/{id}/broadcasters/{broadcasterId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDIO_OWNER')")
    @Operation(summary = "Assign a broadcaster to this studio with an optional custom revenue split")
    public BroadcasterProfileResponse assignBroadcaster(
        @PathVariable UUID id,
        @PathVariable UUID broadcasterId,
        @RequestParam(required = false) @Min(1) @Max(99) Integer revenueSplitPercent
    ) {
        return mapper.toResponse(studioService.assignBroadcaster(id, broadcasterId, revenueSplitPercent));
    }

    @DeleteMapping("/{id}/broadcasters/{broadcasterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDIO_OWNER')")
    @Operation(summary = "Remove a broadcaster from this studio")
    public void removeBroadcaster(@PathVariable UUID id, @PathVariable UUID broadcasterId) {
        studioService.removeBroadcaster(id, broadcasterId);
    }
}
