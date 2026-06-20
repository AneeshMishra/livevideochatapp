package com.platform.catalog.web;

import com.platform.catalog.service.CatalogService;
import com.platform.catalog.web.dto.CategoryResponse;
import com.platform.catalog.web.dto.PagedRoomsResponse;
import com.platform.catalog.web.dto.RoomCardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // ── Public discovery endpoints ─────────────────────────────────────────────

    /**
     * GET /api/v1/catalog/rooms
     * Live broadcaster grid. Served from Redis cache (TTL 10s) on hot path.
     *
     * Query params:
     *   category — filter by category slug (optional)
     *   sort     — "viewers" (default) | "newest"
     *   page     — 0-based page index (default 0)
     *   size     — page size 1–100 (default 24)
     */
    @GetMapping("/rooms")
    public PagedRoomsResponse discoverRooms(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "viewers") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {

        return catalogService.discoverRooms(category, sort, page, size);
    }

    /**
     * GET /api/v1/catalog/search?q=...
     * Full-text search via OpenSearch (no cache — too many unique queries).
     */
    @GetMapping("/search")
    public PagedRoomsResponse searchRooms(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (q == null || q.isBlank()) {
            return new PagedRoomsResponse(List.of(), 0, page, size);
        }
        return catalogService.searchRooms(q.trim(), category, page, size);
    }

    /**
     * GET /api/v1/catalog/rooms/{roomId}
     * Single room card. Redis-cached for 30s.
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<RoomCardResponse> getRoomCard(@PathVariable UUID roomId) {
        return catalogService.getRoomCard(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/catalog/trending?n=20
     * Top N rooms by current viewer count from Redis sorted-set.
     */
    @GetMapping("/trending")
    public List<RoomCardResponse> getTrending(
            @RequestParam(defaultValue = "20") int n) {
        return catalogService.getTrending(n);
    }

    /**
     * GET /api/v1/catalog/categories
     * Ordered list of active categories (for nav menus / filter UI).
     */
    @GetMapping("/categories")
    public List<CategoryResponse> getCategories() {
        return catalogService.listCategories()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    // ── Internal / admin endpoints (JWT with ADMIN role required) ─────────────

    /**
     * POST /api/v1/catalog/rooms
     * Upsert a room entry directly (used by admin tools or internal services).
     */
    @PostMapping("/rooms")
    public ResponseEntity<RoomCardResponse> upsertRoom(@RequestBody UpsertRoomRequest body) {
        RoomCardResponse card = catalogService.upsertRoom(
                UUID.fromString(body.roomId()),
                UUID.fromString(body.broadcasterId()),
                body.title(),
                body.category(),
                body.tags() != null ? body.tags().toArray(new String[0]) : null,
                body.hlsPlaybackUrl()
        );
        return ResponseEntity.ok(card);
    }

    /**
     * PATCH /api/v1/catalog/rooms/{roomId}/thumbnail
     * Update the thumbnail URL (called by the thumbnailer worker).
     */
    @PatchMapping("/rooms/{roomId}/thumbnail")
    public ResponseEntity<Map<String, Boolean>> updateThumbnail(
            @PathVariable UUID roomId,
            @RequestBody ThumbnailRequest body) {
        catalogService.handleThumbnailUpdate(roomId, body.thumbnailUrl());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Infrastructure ─────────────────────────────────────────────────────────

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    // ── Request records ────────────────────────────────────────────────────────

    public record UpsertRoomRequest(
            String roomId,
            String broadcasterId,
            String title,
            String category,
            List<String> tags,
            String hlsPlaybackUrl
    ) {}

    public record ThumbnailRequest(String thumbnailUrl) {}
}
