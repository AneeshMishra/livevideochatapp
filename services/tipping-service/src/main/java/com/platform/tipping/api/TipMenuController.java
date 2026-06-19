package com.platform.tipping.api;

import com.platform.tipping.api.dto.MenuItemRequest;
import com.platform.tipping.api.dto.MenuItemResponse;
import com.platform.tipping.service.TipMenuService;
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
@RequestMapping("/api/v1/tip-menu")
@RequiredArgsConstructor
public class TipMenuController {

    private final TipMenuService menuService;

    // Public — viewers see the menu before sending a tip
    @GetMapping("/broadcasters/{broadcasterId}/items")
    public ResponseEntity<List<MenuItemResponse>> listItems(@PathVariable UUID broadcasterId) {
        List<MenuItemResponse> items = menuService.getActiveItems(broadcasterId).stream()
                .map(MenuItemResponse::from).toList();
        return ResponseEntity.ok(items);
    }

    // Broadcaster-only mutations
    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('BROADCASTER','ADMIN')")
    public ResponseEntity<MenuItemResponse> createItem(
            @RequestBody @Valid MenuItemRequest req,
            Authentication auth) {

        UUID broadcasterId = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(MenuItemResponse.from(
                menuService.createItem(broadcasterId, req.title(), req.description(),
                        req.tokenPrice(), req.position())));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasAnyRole('BROADCASTER','ADMIN')")
    public ResponseEntity<MenuItemResponse> updateItem(
            @PathVariable UUID itemId,
            @RequestBody @Valid MenuItemRequest req,
            Authentication auth) {

        UUID broadcasterId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(MenuItemResponse.from(
                menuService.updateItem(itemId, broadcasterId, req.title(), req.description(),
                        req.tokenPrice(), req.position())));
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasAnyRole('BROADCASTER','ADMIN')")
    public ResponseEntity<Void> deactivateItem(@PathVariable UUID itemId, Authentication auth) {
        UUID broadcasterId = UUID.fromString(auth.getName());
        menuService.deactivateItem(itemId, broadcasterId);
        return ResponseEntity.noContent().build();
    }
}
