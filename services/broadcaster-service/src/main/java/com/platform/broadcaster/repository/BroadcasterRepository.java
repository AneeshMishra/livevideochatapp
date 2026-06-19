package com.platform.broadcaster.repository;

import com.platform.broadcaster.domain.Broadcaster;
import com.platform.broadcaster.domain.BroadcasterStatus;
import com.platform.broadcaster.domain.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BroadcasterRepository extends JpaRepository<Broadcaster, UUID> {

    Optional<Broadcaster> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<Broadcaster> findByStatus(BroadcasterStatus status, Pageable pageable);

    List<Broadcaster> findByStudioId(UUID studioId);

    /** Find all broadcasters visible in a given country (no BLOCK rule for that country). */
    @Query("""
        SELECT b FROM Broadcaster b
        WHERE b.status = :status
          AND NOT EXISTS (
              SELECT 1 FROM GeoBlockRule r
              WHERE r.broadcaster = b
                AND r.countryCode = :countryCode
                AND r.type = 'BLOCK'
          )
        """)
    Page<Broadcaster> findVisibleInCountry(
        @Param("status") BroadcasterStatus status,
        @Param("countryCode") String countryCode,
        Pageable pageable
    );

    long countByKycStatus(KycStatus kycStatus);
}
