package com.platform.tipping.repository;

import com.platform.tipping.domain.TipMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TipMenuItemRepository extends JpaRepository<TipMenuItem, UUID> {

    List<TipMenuItem> findByBroadcasterIdAndActiveTrueOrderByPositionAsc(UUID broadcasterId);

    Optional<TipMenuItem> findByIdAndBroadcasterId(UUID id, UUID broadcasterId);

    int countByBroadcasterIdAndActiveTrue(UUID broadcasterId);
}
