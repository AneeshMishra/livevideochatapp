package com.platform.catalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Maintains a Redis sorted-set that tracks live rooms by viewer count.
 * Key: catalog:trending  Score: viewer_count  Member: roomId
 */
@Service
public class TrendingService {

    private static final String TRENDING_KEY = "catalog:trending";

    private final StringRedisTemplate redis;
    private final int maxRooms;

    public TrendingService(StringRedisTemplate redis,
                           @Value("${catalog.trending-max-rooms:50}") int maxRooms) {
        this.redis = redis;
        this.maxRooms = maxRooms;
    }

    public void updateViewerCount(String roomId, long count) {
        redis.opsForZSet().add(TRENDING_KEY, roomId, count);
        // Keep only top N rooms in the set (trim the tail)
        redis.opsForZSet().removeRange(TRENDING_KEY, 0, -(maxRooms + 1));
    }

    public void removeRoom(String roomId) {
        redis.opsForZSet().remove(TRENDING_KEY, roomId);
    }

    /** Returns top N room IDs ordered by viewer count descending. */
    public List<String> getTopRoomIds(int n) {
        Set<String> ids = redis.opsForZSet().reverseRange(TRENDING_KEY, 0, (long) n - 1);
        return ids == null ? List.of() : List.copyOf(ids);
    }
}
