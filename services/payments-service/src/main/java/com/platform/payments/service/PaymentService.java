package com.platform.payments.service;

import com.platform.payments.config.TokenPackageProperties;
import com.platform.payments.domain.ChargebackRecord;
import com.platform.payments.domain.OrderStatus;
import com.platform.payments.domain.PaymentOrder;
import com.platform.payments.domain.PaymentProvider;
import com.platform.payments.event.PaymentEvent;
import com.platform.payments.event.PaymentEventPublisher;
import com.platform.payments.exception.OrderNotFoundException;
import com.platform.payments.exception.PackageNotFoundException;
import com.platform.payments.repository.ChargebackRepository;
import com.platform.payments.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentOrderRepository orderRepo;
    private final ChargebackRepository chargebackRepo;
    private final PaymentEventPublisher eventPublisher;
    private final TokenPackageProperties packageProperties;

    @Value("${app.providers.ccbill.account-number:888888}")
    private String ccbillAccountNumber;

    @Value("${app.providers.ccbill.sub-account:0000}")
    private String ccbillSubAccount;

    @Value("${app.providers.ccbill.flex-form-id:}")
    private String ccbillFlexFormId;

    @Value("${app.providers.ccbill.base-url:https://billing.ccbill.com/jpost/billing.cgi}")
    private String ccbillBaseUrl;

    @Value("${app.providers.epoch.merchant-id:}")
    private String epochMerchantId;

    @Value("${app.providers.epoch.base-url:https://epoch.com/purchase}")
    private String epochBaseUrl;

    @Value("${app.providers.segpay.merchant-id:}")
    private String segpayMerchantId;

    @Value("${app.providers.segpay.base-url:https://cgp.segpay.com/cgi/billing.cgi}")
    private String segpayBaseUrl;

    /**
     * Creates a payment order and returns the provider redirect URL.
     * The caller must redirect the user's browser to that URL.
     */
    public PaymentOrder createOrder(UUID userId, long packageId, PaymentProvider provider) {
        TokenPackageProperties.TokenPackage pkg = packageProperties.getTokenPackages().stream()
                .filter(p -> p.getId() == packageId)
                .findFirst()
                .orElseThrow(() -> new PackageNotFoundException(packageId));

        // Idempotency key scoped to user + package + provider + millisecond timestamp
        // (allows a user to start multiple concurrent purchases of different packages)
        String idempotencyKey = "order:" + userId + ":" + packageId + ":" + provider
                + ":" + Instant.now().toEpochMilli();

        PaymentOrder order = PaymentOrder.create(
                userId, provider,
                pkg.getTokens(),
                new BigDecimal(pkg.getPrice()),
                pkg.getCurrency(),
                idempotencyKey);

        // Set to PROCESSING immediately — user is being redirected to provider page
        order.setStatus(OrderStatus.PROCESSING);
        order.setProviderRedirectUrl(buildProviderUrl(order.getId(), pkg, provider));

        PaymentOrder saved = orderRepo.save(order);
        log.info("Payment order created orderId={} userId={} provider={} tokens={}",
                saved.getId(), userId, provider, pkg.getTokens());
        return saved;
    }

    /**
     * Called by webhook handlers after provider signature verification.
     * Looks up by orderId (if known) or by (provider, providerOrderId).
     * Idempotent — safe to call multiple times for the same webhook delivery.
     */
    public PaymentOrder confirmPayment(PaymentProvider provider, String providerOrderId,
                                       UUID orderId, String rawPayload) {
        PaymentOrder order = resolveOrder(provider, providerOrderId, orderId);

        if (order.getStatus() == OrderStatus.COMPLETED) {
            log.info("Webhook replay ignored — already completed orderId={}", order.getId());
            return order;
        }

        order.setProviderOrderId(providerOrderId);
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        order.setRawWebhookPayload(rawPayload);

        PaymentOrder saved = orderRepo.save(order);

        // wallet-service consumes this event and credits the user's token balance
        eventPublisher.publish(new PaymentEvent.PaymentCompleted(
                saved.getId(),
                saved.getUserId(),
                saved.getTokenAmount(),
                saved.getProvider(),
                saved.getIdempotencyKey(),  // wallet uses this as its own idempotency key
                saved.getCompletedAt()));

        log.info("Payment confirmed orderId={} userId={} tokens={}",
                saved.getId(), saved.getUserId(), saved.getTokenAmount());
        return saved;
    }

    /**
     * Called by webhook handlers when a payment is declined/failed.
     * Idempotent — re-applying FAILED to an already-FAILED order is a no-op.
     */
    public PaymentOrder failOrder(PaymentProvider provider, String providerOrderId,
                                   UUID orderId, String reason, String rawPayload) {
        PaymentOrder order = resolveOrder(provider, providerOrderId, orderId);

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.FAILED) {
            return order;
        }

        order.setProviderOrderId(providerOrderId);
        order.setStatus(OrderStatus.FAILED);
        order.setErrorMessage(truncate(reason, 500));
        order.setRawWebhookPayload(rawPayload);

        PaymentOrder saved = orderRepo.save(order);
        eventPublisher.publish(new PaymentEvent.PaymentFailed(
                saved.getId(), saved.getUserId(), reason, Instant.now()));

        log.info("Payment failed orderId={} userId={} reason={}", saved.getId(), saved.getUserId(), reason);
        return saved;
    }

    /**
     * Records a chargeback from the provider and marks the order accordingly.
     * Publishes a ChargebackReceived event so other services (e.g., broadcaster payout freeze) can react.
     */
    public void processChargeback(UUID paymentOrderId, String providerChargebackId,
                                   BigDecimal chargebackAmount, String currencyCode, String reason) {
        // Idempotent — skip if we've already processed this chargeback
        if (chargebackRepo.existsByProviderChargebackId(providerChargebackId)) {
            log.info("Chargeback already recorded providerChargebackId={}", providerChargebackId);
            return;
        }

        PaymentOrder order = orderRepo.findById(paymentOrderId)
                .orElseThrow(() -> new OrderNotFoundException(paymentOrderId));

        order.setStatus(OrderStatus.CHARGEBACK);
        orderRepo.save(order);

        ChargebackRecord cb = ChargebackRecord.of(
                paymentOrderId, order.getUserId(),
                providerChargebackId, chargebackAmount, currencyCode, reason);
        chargebackRepo.save(cb);

        eventPublisher.publish(new PaymentEvent.ChargebackReceived(
                paymentOrderId, order.getUserId(),
                chargebackAmount, currencyCode, reason, Instant.now()));

        log.warn("Chargeback recorded orderId={} userId={} amount={} {}",
                paymentOrderId, order.getUserId(), chargebackAmount, currencyCode);
    }

    @Transactional(readOnly = true)
    public Page<PaymentOrder> getUserOrders(UUID userId, Pageable pageable) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public PaymentOrder getOrder(UUID orderId, UUID requestingUserId, boolean isAdmin) {
        PaymentOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        // Non-admins can only see their own orders; return 404 to avoid leaking existence
        if (!isAdmin && !order.getUserId().equals(requestingUserId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private PaymentOrder resolveOrder(PaymentProvider provider, String providerOrderId, UUID orderId) {
        if (orderId != null) {
            return orderRepo.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));
        }
        return orderRepo.findByProviderAndProviderOrderIdForUpdate(provider, providerOrderId)
                .orElseThrow(() -> new OrderNotFoundException("provider=" + provider + " txId=" + providerOrderId));
    }

    private String buildProviderUrl(UUID orderId, TokenPackageProperties.TokenPackage pkg,
                                     PaymentProvider provider) {
        return switch (provider) {
            case CCBILL -> String.format(
                    "%s?clientAccnum=%s&clientSubacc=%s&formName=%s"
                    + "&initialPrice=%s&initialPeriod=2&currencyCode=840&X-orderId=%s",
                    ccbillBaseUrl, ccbillAccountNumber, ccbillSubAccount, ccbillFlexFormId,
                    pkg.getPrice(), orderId);
            case EPOCH -> String.format(
                    "%s?mid=%s&amount=%s&currency=USD&x_order_id=%s",
                    epochBaseUrl, epochMerchantId, pkg.getPrice(), orderId);
            case SEGPAY -> String.format(
                    "%s?mid=%s&amount=%s&currency=USD&order_id=%s",
                    segpayBaseUrl, segpayMerchantId, pkg.getPrice(), orderId);
            default -> throw new UnsupportedOperationException("Provider not yet integrated: " + provider);
        };
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
