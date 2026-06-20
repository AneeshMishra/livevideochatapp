package com.platform.catalog.repository;

import com.platform.catalog.domain.CatalogRoom;
import com.platform.catalog.domain.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface CatalogRoomRepository extends JpaRepository<CatalogRoom, UUID> {

    Page<CatalogRoom> findByStatus(RoomStatus status, Pageable pageable);

    Page<CatalogRoom> findByStatusAndCategory(RoomStatus status, String category, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE CatalogRoom r
               SET r.status = :status,
                   r.streamStartedAt = :startedAt
             WHERE r.id = :id
            """)
    int updateStatus(@Param("id") UUID id,
                     @Param("status") RoomStatus status,
                     @Param("startedAt") OffsetDateTime startedAt);

    @Modifying
    @Query("""
            UPDATE CatalogRoom r
               SET r.viewerCount = :count,
                   r.peakViewerCount = CASE WHEN :count > r.peakViewerCount THEN :count ELSE r.peakViewerCount END
             WHERE r.id = :id
            """)
    int updateViewerCount(@Param("id") UUID id, @Param("count") long count);

    @Modifying
    @Query("""
            UPDATE CatalogRoom r
               SET r.thumbnailUrl = :url
             WHERE r.id = :id
            """)
    int updateThumbnail(@Param("id") UUID id, @Param("url") String url);

    @Modifying
    @Query("""
            UPDATE CatalogRoom r
               SET r.deliveryMode = :mode
             WHERE r.id = :id
            """)
    int updateDeliveryMode(@Param("id") UUID id, @Param("mode") String mode);
}
