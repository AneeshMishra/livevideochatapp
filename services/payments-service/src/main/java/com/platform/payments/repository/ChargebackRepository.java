package com.platform.payments.repository;

import com.platform.payments.domain.ChargebackRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChargebackRepository extends JpaRepository<ChargebackRecord, UUID> {

    List<ChargebackRecord> findByUserId(UUID userId);

    boolean existsByProviderChargebackId(String providerChargebackId);
}
