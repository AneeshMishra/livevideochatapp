package com.platform.broadcaster.api;

import com.platform.broadcaster.api.dto.*;
import com.platform.broadcaster.domain.BroadcasterStatus;
import com.platform.broadcaster.service.BroadcasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/broadcasters")
@RequiredArgsConstructor
@Tag(name = "Broadcasters", description = "Broadcaster profile and stream management")
public class BroadcasterController {

    private final BroadcasterService broadcasterService;
    private final BroadcasterMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new broadcaster account")
    public BroadcasterProfileResponse register(@Valid @RequestBody CreateBroadcasterRequest req) {
        return mapper.toResponse(broadcasterService.register(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get broadcaster profile by internal ID")
    public BroadcasterProfileResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(broadcasterService.getById(id));
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Get broadcaster profile by user ID (from identity service)")
    public BroadcasterProfileResponse getByUserId(@PathVariable UUID userId) {
        return mapper.toResponse(broadcasterService.getByUserId(userId));
    }

    @GetMapping("/online")
    @Operation(summary = "Paginated list of currently online broadcasters, optionally filtered by country")
    public Page<BroadcasterProfileResponse> listOnline(
        @RequestParam(required = false) String countryCode,
        @PageableDefault(size = 30) Pageable pageable
    ) {
        if (countryCode != null) {
            return broadcasterService.listOnlineVisibleInCountry(countryCode.toUpperCase(), pageable)
                .map(mapper::toResponse);
        }
        return broadcasterService.listOnline(pageable).map(mapper::toResponse);
    }

    @PatchMapping("/{id}/profile")
    @PreAuthorize("hasRole('BROADCASTER') and #id == authentication.principal.broadcasterId or hasRole('ADMIN')")
    @Operation(summary = "Update display name, bio, or avatar")
    public BroadcasterProfileResponse updateProfile(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateBroadcasterRequest req
    ) {
        return mapper.toResponse(broadcasterService.updateProfile(id, req));
    }

    @PutMapping("/{id}/stream-settings")
    @PreAuthorize("hasRole('BROADCASTER') and #id == authentication.principal.broadcasterId or hasRole('ADMIN')")
    @Operation(summary = "Update stream title, tags, category, and show pricing")
    public BroadcasterProfileResponse updateStreamSettings(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateStreamSettingsRequest req
    ) {
        return mapper.toResponse(broadcasterService.updateStreamSettings(id, req));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('BROADCASTER') and #id == authentication.principal.broadcasterId or hasRole('ADMIN')")
    @Operation(summary = "Change broadcaster status (go live, go offline, etc.)")
    public BroadcasterProfileResponse changeStatus(
        @PathVariable UUID id,
        @RequestParam BroadcasterStatus status
    ) {
        return mapper.toResponse(broadcasterService.changeStatus(id, status));
    }

    // --- KYC (admin-only; vendor posts results via this endpoint) ---

    @PutMapping("/{id}/kyc")
    @PreAuthorize("hasRole('ADMIN') or hasRole('KYC_SERVICE')")
    @Operation(summary = "Update KYC / 2257 verification status (internal/admin use only)")
    public BroadcasterProfileResponse updateKyc(
        @PathVariable UUID id,
        @Valid @RequestBody KycUpdateRequest req
    ) {
        return mapper.toResponse(broadcasterService.updateKycStatus(id, req));
    }

    // --- Tip menu ---

    @PostMapping("/{id}/tip-menu")
    @PreAuthorize("hasRole('BROADCASTER') and #id == authentication.principal.broadcasterId or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a tip menu item")
    public TipMenuItemResponse addTipMenuItem(
        @PathVariable UUID id,
        @Valid @RequestBody UpsertTipMenuItemRequest req
    ) {
        return mapper.toResponse(broadcasterService.addTipMenuItem(id, req));
    }

    @DeleteMapping("/{id}/tip-menu/{itemId}")
    @PreAuthorize("hasRole('BROADCASTER') and #id == authentication.principal.broadcasterId or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a tip menu item")
    public void removeTipMenuItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        broadcasterService.removeTipMenuItem(id, itemId);
    }

    // --- Geo-block ---

    @PostMapping("/{id}/geo-blocks")
    @PreAuthorize("hasRole('BROADCASTER') and #id == authentication.principal.broadcasterId or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a geo-block rule for a country")
    public GeoBlockRuleResponse addGeoBlockRule(
        @PathVariable UUID id,
        @Valid @RequestBody AddGeoBlockRuleRequest req
    ) {
        return mapper.toResponse(broadcasterService.addGeoBlockRule(id, req));
    }

    @DeleteMapping("/{id}/geo-blocks/{countryCode}")
    @PreAuthorize("hasRole('BROADCASTER') and #id == authentication.principal.broadcasterId or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a geo-block rule")
    public void removeGeoBlockRule(@PathVariable UUID id, @PathVariable String countryCode) {
        broadcasterService.removeGeoBlockRule(id, countryCode.toUpperCase());
    }
}
