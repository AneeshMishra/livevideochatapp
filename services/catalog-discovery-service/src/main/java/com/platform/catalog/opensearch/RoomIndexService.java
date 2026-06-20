package com.platform.catalog.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.catalog.domain.CatalogRoom;
import jakarta.annotation.PostConstruct;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class RoomIndexService {

    private static final Logger log = LoggerFactory.getLogger(RoomIndexService.class);

    private final OpenSearchClient client;
    private final ObjectMapper objectMapper;

    @Value("${opensearch.index.rooms}")
    private String roomsIndex;

    public RoomIndexService(OpenSearchClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = client.indices()
                    .exists(ExistsRequest.of(e -> e.index(roomsIndex)))
                    .value();
            if (!exists) {
                createIndex();
                log.info("Created OpenSearch index: {}", roomsIndex);
            }
        } catch (IOException e) {
            log.warn("Could not ensure OpenSearch index existence: {}", e.getMessage());
        }
    }

    private void createIndex() throws IOException {
        // Index settings optimised for a single-node dev cluster.
        // In production: increase shards and replicas.
        client.indices().create(CreateIndexRequest.of(c -> c
                .index(roomsIndex)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )
                .mappings(m -> m
                        .properties("room_id",               p -> p.keyword(k -> k))
                        .properties("broadcaster_id",         p -> p.keyword(k -> k))
                        .properties("broadcaster_username",   p -> p.keyword(k -> k))
                        .properties("broadcaster_display_name", p -> p.text(t -> t
                                .fields("keyword", f -> f.keyword(k -> k))))
                        .properties("title",                  p -> p.text(t -> t.analyzer("standard")))
                        .properties("category",               p -> p.keyword(k -> k))
                        .properties("tags",                   p -> p.keyword(k -> k))
                        .properties("status",                 p -> p.keyword(k -> k))
                        .properties("delivery_mode",          p -> p.keyword(k -> k))
                        .properties("viewer_count",           p -> p.long_(l -> l))
                        .properties("hls_playback_url",       p -> p.keyword(k -> k.index(false)))
                        .properties("thumbnail_url",          p -> p.keyword(k -> k.index(false)))
                        .properties("is_featured",            p -> p.boolean_(b -> b))
                        .properties("stream_started_at",      p -> p.date(d -> d))
                        .properties("updated_at",             p -> p.date(d -> d))
                )
        ));
    }

    @Async
    public void indexRoom(CatalogRoom room) {
        try {
            RoomDocument doc = toDocument(room);
            client.index(i -> i
                    .index(roomsIndex)
                    .id(room.getId().toString())
                    .document(doc)
            );
        } catch (IOException e) {
            log.warn("Failed to index room {}: {}", room.getId(), e.getMessage());
        }
    }

    @Async
    public void updateViewerCount(String roomId, long count) {
        try {
            client.update(u -> u
                    .index(roomsIndex)
                    .id(roomId)
                    .doc(Map.of("viewer_count", count)),
                    RoomDocument.class
            );
        } catch (IOException e) {
            log.warn("Failed to update viewer count for room {}: {}", roomId, e.getMessage());
        }
    }

    @Async
    public void updateStatus(String roomId, String status) {
        try {
            client.update(u -> u
                    .index(roomsIndex)
                    .id(roomId)
                    .doc(Map.of("status", status)),
                    RoomDocument.class
            );
        } catch (IOException e) {
            log.warn("Failed to update status for room {}: {}", roomId, e.getMessage());
        }
    }

    @Async
    public void updateDeliveryMode(String roomId, String deliveryMode) {
        try {
            client.update(u -> u
                    .index(roomsIndex)
                    .id(roomId)
                    .doc(Map.of("delivery_mode", deliveryMode)),
                    RoomDocument.class
            );
        } catch (IOException e) {
            log.warn("Failed to update delivery_mode for room {}: {}", roomId, e.getMessage());
        }
    }

    @Async
    public void updateThumbnail(String roomId, String thumbnailUrl) {
        try {
            client.update(u -> u
                    .index(roomsIndex)
                    .id(roomId)
                    .doc(Map.of("thumbnail_url", thumbnailUrl)),
                    RoomDocument.class
            );
        } catch (IOException e) {
            log.warn("Failed to update thumbnail for room {}: {}", roomId, e.getMessage());
        }
    }

    public List<RoomDocument> discoverRooms(String category, String sort, int from, int size) {
        try {
            SearchResponse<RoomDocument> response = client.search(s -> {
                s.index(roomsIndex)
                 .from(from)
                 .size(size)
                 .query(q -> q.bool(b -> {
                     b.filter(f -> f.term(t -> t.field("status").value("LIVE")));
                     if (category != null && !category.isBlank()) {
                         b.filter(f -> f.term(t -> t.field("category").value(category)));
                     }
                     return b;
                 }));

                if ("newest".equalsIgnoreCase(sort)) {
                    s.sort(so -> so.field(f -> f.field("stream_started_at").order(SortOrder.Desc)));
                } else {
                    // default: most viewers first
                    s.sort(so -> so.field(f -> f.field("viewer_count").order(SortOrder.Desc)));
                }
                return s;
            }, RoomDocument.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();

        } catch (IOException e) {
            log.warn("OpenSearch discoverRooms failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<RoomDocument> searchRooms(String query, String category, int from, int size) {
        try {
            SearchResponse<RoomDocument> response = client.search(s -> s
                    .index(roomsIndex)
                    .from(from)
                    .size(size)
                    .query(q -> q.bool(b -> {
                        b.must(m -> m.multiMatch(mm -> mm
                                .fields("title^2", "broadcaster_username", "broadcaster_display_name", "tags")
                                .query(query)
                                .type(TextQueryType.BestFields)
                        ));
                        b.filter(f -> f.term(t -> t.field("status").value("LIVE")));
                        if (category != null && !category.isBlank()) {
                            b.filter(f -> f.term(t -> t.field("category").value(category)));
                        }
                        return b;
                    })),
                    RoomDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();

        } catch (IOException e) {
            log.warn("OpenSearch searchRooms failed: {}", e.getMessage());
            return List.of();
        }
    }

    public long countLiveRooms(String category) {
        try {
            var response = client.count(c -> {
                c.index(roomsIndex).query(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("status").value("LIVE")));
                    if (category != null && !category.isBlank()) {
                        b.filter(f -> f.term(t -> t.field("category").value(category)));
                    }
                    return b;
                }));
                return c;
            });
            return response.count();
        } catch (IOException e) {
            log.warn("OpenSearch count failed: {}", e.getMessage());
            return 0;
        }
    }

    private RoomDocument toDocument(CatalogRoom room) {
        var doc = new RoomDocument();
        doc.setRoomId(room.getId().toString());
        doc.setBroadcasterId(room.getBroadcasterId().toString());
        doc.setBroadcasterUsername(room.getBroadcasterUsername());
        doc.setBroadcasterDisplayName(room.getBroadcasterDisplayName());
        doc.setBroadcasterAvatarUrl(room.getBroadcasterAvatarUrl());
        doc.setTitle(room.getTitle());
        doc.setCategory(room.getCategory());
        doc.setTags(room.getTags() != null ? Arrays.asList(room.getTags()) : List.of());
        doc.setStatus(room.getStatus().name());
        doc.setDeliveryMode(room.getDeliveryMode());
        doc.setViewerCount(room.getViewerCount());
        doc.setHlsPlaybackUrl(room.getHlsPlaybackUrl());
        doc.setThumbnailUrl(room.getThumbnailUrl());
        doc.setFeatured(room.isFeatured());
        if (room.getStreamStartedAt() != null) {
            doc.setStreamStartedAt(room.getStreamStartedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (room.getUpdatedAt() != null) {
            doc.setUpdatedAt(room.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        return doc;
    }
}
