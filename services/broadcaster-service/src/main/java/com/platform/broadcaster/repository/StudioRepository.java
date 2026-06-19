package com.platform.broadcaster.repository;

import com.platform.broadcaster.domain.Studio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudioRepository extends JpaRepository<Studio, UUID> {

    Optional<Studio> findByOwnerId(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);
}
