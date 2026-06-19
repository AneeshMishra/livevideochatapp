package com.platform.broadcaster.repository;

import com.platform.broadcaster.domain.GeoBlockRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GeoBlockRuleRepository extends JpaRepository<GeoBlockRule, UUID> {

    List<GeoBlockRule> findByBroadcasterId(UUID broadcasterId);

    boolean existsByBroadcasterIdAndCountryCode(UUID broadcasterId, String countryCode);

    @Modifying
    @Query("DELETE FROM GeoBlockRule r WHERE r.broadcaster.id = :broadcasterId AND r.countryCode = :countryCode")
    void deleteByBroadcasterIdAndCountryCode(
        @Param("broadcasterId") UUID broadcasterId,
        @Param("countryCode") String countryCode
    );
}
