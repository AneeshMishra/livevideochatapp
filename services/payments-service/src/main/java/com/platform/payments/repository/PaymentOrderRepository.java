package com.platform.payments.repository;

import com.platform.payments.domain.OrderStatus;
import com.platform.payments.domain.PaymentOrder;
import com.platform.payments.domain.PaymentProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    // Pessimistic write lock used in confirmPayment / failOrder to prevent concurrent double-completion
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentOrder o WHERE o.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentOrder o WHERE o.provider = :provider AND o.providerOrderId = :providerOrderId")
    Optional<PaymentOrder> findByProviderAndProviderOrderIdForUpdate(
            @Param("provider") PaymentProvider provider,
            @Param("providerOrderId") String providerOrderId);

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndStatus(UUID userId, OrderStatus status);
}
