package com.platform.userprofile.repository;

import com.platform.userprofile.domain.BlockEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockRepository extends JpaRepository<BlockEntry, UUID> {

    Optional<BlockEntry> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    Page<BlockEntry> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId, Pageable pageable);
}
