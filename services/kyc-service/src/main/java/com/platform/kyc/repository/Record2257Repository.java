package com.platform.kyc.repository;

import com.platform.kyc.domain.Record2257;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface Record2257Repository extends JpaRepository<Record2257, UUID> {

    Optional<Record2257> findByBroadcasterId(UUID broadcasterId);

    boolean existsByBroadcasterId(UUID broadcasterId);
}
