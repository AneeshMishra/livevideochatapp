package com.platform.tipping.repository;

import com.platform.tipping.domain.GiftType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GiftTypeRepository extends JpaRepository<GiftType, UUID> {

    List<GiftType> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<GiftType> findBySlugAndActiveTrue(String slug);
}
