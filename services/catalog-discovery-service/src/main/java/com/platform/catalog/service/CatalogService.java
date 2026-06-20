package com.platform.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.catalog.domain.CatalogRoom;
import com.platform.catalog.domain.Category;
import com.platform.catalog.domain.RoomStatus;
import com.platform.catalog.opensearch.RoomDocument;
import com.platform.catalog.opensearch.RoomIndexService;
import com.platform.catalog.repository.CatalogRoomRepository;
import com.platform.catalog.repository.CategoryRepository;
import com.platform.catalog.web.dto.PagedRoomsResponse;
import com.platform.catalog.web.dto.RoomCardResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private static final String ROOM_CACHE_PREFIX = "catalog:room:";
    private static final String GRID_CACHE_PREFIX  = "catalog:grid:";

    private final CatalogRoomRepository roomRepo;
    private final CategoryRepository categoryRepo;
    private final RoomIndexService indexService;
    private final TrendingService trendingService;
    private final BroadcasterClient broadcasterClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private final Duration gridCacheTtl;
    private final Duration roomCacheTtl;

    public CatalogService(
            CatalogRoomRepository roomRepo,
            CategoryRepository categoryRepo,
            RoomIndexService indexService,
            TrendingService trendingService,
            BroadcasterClient broadcasterClient,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${catalog.grid-cache-ttl-seconds:10}") int gridTtlSec,
            @Value("${catalog.room-cache-ttl-seconds:30}") int roomTtlSec) {

        this.roomRepo = roomRepo;
        this.categoryRepo = categoryRepo;
        this.indexService = indexService;
        this.trendingService = trendingService;
        this.broadcasterClient = broadcasterClient;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.gridCacheTtl = Duration.ofSeconds(gridTtlSec);
        this.roomCacheTtl = Duration.ofSeconds(roomTtlSec);
    }

    // ── Discovery grid ─────────────────────────────────────────────────────────

    public PagedRoomsResponse discoverRooms(String category, String sort, int page, int size) {
        size = Math.min(size, 100); // cap per-page size

        String cacheKey = GRID_CACHE_PREFIX + category + ":" + sort + ":" + page + ":" + size;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, PagedRoomsResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Grid cache deserialise failed, falling through to OpenSearch");
            }
        }

        List<RoomDocument> docs = indexService.discoverRooms(category, sort, page * size, size);
        long total = indexService.countLiveRooms(category);
        List<RoomCardResponse> cards = docs.stream().map(RoomCardResponse::fromDocument).toList();

        PagedRoomsResponse result = new PagedRoomsResponse(cards, total, page, size);
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), gridCacheTtl);
        } catch (JsonProcessingException e) {
            log.warn("Grid cache serialise failed: {}", e.getMessage());
        }
        return result;
    }

    // ── Full-text search ───────────────────────────────────────────────────────

    public PagedRoomsResponse searchRooms(String query, String category, int page, int size) {
        size = Math.min(size, 50);
        List<RoomDocument> docs = indexService.searchRooms(query, category, page * size, size);
        List<RoomCardResponse> cards = docs.stream().map(RoomCardResponse::fromDocument).toList();
        return new PagedRoomsResponse(cards, cards.size(), page, size);
    }

    // ── Single room card (Redis cache → OpenSearch) ───────────────────────────

    public Optional<RoomCardResponse> getRoomCard(UUID roomId) {
        String cacheKey = ROOM_CACHE_PREFIX + roomId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return Optional.of(objectMapper.readValue(cached, RoomCardResponse.class));
            } catch (JsonProcessingException ignored) {}
        }

        return roomRepo.findById(roomId).map(room -> {
            RoomCardResponse card = RoomCardResponse.fromEntity(room);
            try {
                redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(card), roomCacheTtl);
            } catch (JsonProcessingException ignored) {}
            return card;
        });
    }

    // ── Trending ───────────────────────────────────────────────────────────────

    public List<RoomCardResponse> getTrending(int n) {
        List<String> roomIds = trendingService.getTopRoomIds(Math.min(n, 50));
        return roomIds.stream()
                .map(id -> {
                    try {
                        return getRoomCard(UUID.fromString(id));
                    } catch (IllegalArgumentException e) {
                        return Optional.<RoomCardResponse>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    // ── Categories ─────────────────────────────────────────────────────────────

    public List<Category> listCategories() {
        return categoryRepo.findByActiveTrueOrderByDisplayOrderAsc();
    }

    // ── Event-driven upserts (called from Kafka consumers) ────────────────────

    @Transactional
    public void handleStreamStarted(String roomId, String broadcasterId,
                                    String hlsPlaybackUrl, String title,
                                    String category, String[] tags) {
        UUID roomUuid = UUID.fromString(roomId);
        UUID broadcasterUuid = UUID.fromString(broadcasterId);

        CatalogRoom room = roomRepo.findById(roomUuid).orElse(new CatalogRoom());
        room.setId(roomUuid);
        room.setBroadcasterId(broadcasterUuid);
        room.setStatus(RoomStatus.LIVE);
        room.setStreamStartedAt(OffsetDateTime.now());
        if (hlsPlaybackUrl != null) room.setHlsPlaybackUrl(hlsPlaybackUrl);
        if (title != null && !title.isBlank()) room.setTitle(title);
        if (category != null) room.setCategory(category);
        if (tags != null) room.setTags(tags);

        // Enrich with broadcaster profile (best-effort; log and skip on failure).
        if (room.getBroadcasterUsername() == null) {
            broadcasterClient.fetchProfile(broadcasterId).ifPresent(p -> {
                room.setBroadcasterUsername(
                        p.getUsername() != null ? p.getUsername() : "broadcaster_" + broadcasterId.substring(0, 8));
                room.setBroadcasterDisplayName(p.getDisplayName());
                room.setBroadcasterAvatarUrl(p.getAvatarUrl());
            });
        }
        if (room.getBroadcasterUsername() == null) {
            room.setBroadcasterUsername("broadcaster_" + broadcasterId.substring(0, 8));
        }

        roomRepo.save(room);
        indexService.indexRoom(room);
        trendingService.updateViewerCount(roomId, room.getViewerCount());
        evictGridCache();

        log.info("Room {} marked LIVE in catalog", roomId);
    }

    @Transactional
    public void handleStreamEnded(String roomId) {
        UUID roomUuid = UUID.fromString(roomId);
        int updated = roomRepo.updateStatus(roomUuid, RoomStatus.OFFLINE, null);
        if (updated > 0) {
            indexService.updateStatus(roomId, "OFFLINE");
            trendingService.removeRoom(roomId);
            evictRoomCache(roomId);
            evictGridCache();
            log.info("Room {} marked OFFLINE in catalog", roomId);
        }
    }

    @Transactional
    public void handleViewerCountUpdate(String roomId, long count) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            roomRepo.updateViewerCount(roomUuid, count);
            indexService.updateViewerCount(roomId, count);
            trendingService.updateViewerCount(roomId, count);
            evictRoomCache(roomId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid roomId in presence event: {}", roomId);
        }
    }

    @Transactional
    public void handleDeliveryModeChange(String roomId, String deliveryMode) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            roomRepo.updateDeliveryMode(roomUuid, deliveryMode);
            indexService.updateDeliveryMode(roomId, deliveryMode);
            evictRoomCache(roomId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid roomId in delivery mode event: {}", roomId);
        }
    }

    @Transactional
    public void handleThumbnailUpdate(UUID roomId, String thumbnailUrl) {
        roomRepo.updateThumbnail(roomId, thumbnailUrl);
        indexService.updateThumbnail(roomId.toString(), thumbnailUrl);
        evictRoomCache(roomId.toString());
    }

    // ── Internal admin ─────────────────────────────────────────────────────────

    @Transactional
    public RoomCardResponse upsertRoom(UUID roomId, UUID broadcasterId, String title,
                                       String category, String[] tags, String hlsPlaybackUrl) {
        CatalogRoom room = roomRepo.findById(roomId).orElse(new CatalogRoom());
        room.setId(roomId);
        room.setBroadcasterId(broadcasterId);
        if (title != null) room.setTitle(title);
        if (category != null) room.setCategory(category);
        if (tags != null) room.setTags(tags);
        if (hlsPlaybackUrl != null) room.setHlsPlaybackUrl(hlsPlaybackUrl);
        if (room.getBroadcasterUsername() == null) {
            room.setBroadcasterUsername("broadcaster_" + broadcasterId.toString().substring(0, 8));
        }
        room = roomRepo.save(room);
        indexService.indexRoom(room);
        return RoomCardResponse.fromEntity(room);
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private void evictRoomCache(String roomId) {
        redis.delete(ROOM_CACHE_PREFIX + roomId);
    }

    private void evictGridCache() {
        // Pattern delete is a scan — acceptable in dev; use a cache invalidation tag in prod.
        var keys = redis.keys(GRID_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
