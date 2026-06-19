package com.platform.payments.service;

import com.platform.payments.config.TokenPackageProperties;
import com.platform.payments.domain.OrderStatus;
import com.platform.payments.domain.PaymentOrder;
import com.platform.payments.domain.PaymentProvider;
import com.platform.payments.event.PaymentEvent;
import com.platform.payments.event.PaymentEventPublisher;
import com.platform.payments.exception.OrderNotFoundException;
import com.platform.payments.exception.PackageNotFoundException;
import com.platform.payments.repository.ChargebackRepository;
import com.platform.payments.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentOrderRepository orderRepo;
    @Mock ChargebackRepository chargebackRepo;
    @Mock PaymentEventPublisher eventPublisher;
    @Mock TokenPackageProperties packageProperties;

    @InjectMocks PaymentService paymentService;

    private TokenPackageProperties.TokenPackage pkg100;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Mockito can't wire
        ReflectionTestUtils.setField(paymentService, "ccbillAccountNumber", "888888");
        ReflectionTestUtils.setField(paymentService, "ccbillSubAccount", "0000");
        ReflectionTestUtils.setField(paymentService, "ccbillFlexFormId", "testform");
        ReflectionTestUtils.setField(paymentService, "ccbillBaseUrl", "https://billing.ccbill.com/jpost/billing.cgi");
        ReflectionTestUtils.setField(paymentService, "epochMerchantId", "");
        ReflectionTestUtils.setField(paymentService, "epochBaseUrl", "https://epoch.com/purchase");
        ReflectionTestUtils.setField(paymentService, "segpayMerchantId", "");
        ReflectionTestUtils.setField(paymentService, "segpayBaseUrl", "https://cgp.segpay.com/cgi/billing.cgi");

        pkg100 = new TokenPackageProperties.TokenPackage();
        pkg100.setId(1L);
        pkg100.setTokens(100L);
        pkg100.setPrice("9.99");
        pkg100.setCurrency("USD");
        pkg100.setLabel("Starter");
    }

    // ── createOrder ──────────────────────────────────────────────────────────

    @Test
    void createOrder_validPackage_savesOrderAndReturnsRedirectUrl() {
        UUID userId = UUID.randomUUID();
        when(packageProperties.getTokenPackages()).thenReturn(List.of(pkg100));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentOrder result = paymentService.createOrder(userId, 1L, PaymentProvider.CCBILL);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getTokenAmount()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(result.getProviderRedirectUrl()).contains("ccbill.com");
        verify(orderRepo).save(any());
    }

    @Test
    void createOrder_unknownPackage_throws() {
        when(packageProperties.getTokenPackages()).thenReturn(List.of(pkg100));
        assertThatThrownBy(() -> paymentService.createOrder(UUID.randomUUID(), 99L, PaymentProvider.CCBILL))
                .isInstanceOf(PackageNotFoundException.class);
    }

    // ── confirmPayment ────────────────────────────────────────────────────────

    @Test
    void confirmPayment_pendingOrder_completesAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        PaymentOrder order = buildOrder(orderId, OrderStatus.PROCESSING);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentOrder result = paymentService.confirmPayment(
                PaymentProvider.CCBILL, "TXN-001", orderId, "{}");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.getProviderOrderId()).isEqualTo("TXN-001");
        assertThat(result.getCompletedAt()).isNotNull();

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PaymentEvent.PaymentCompleted.class);
        assertThat(((PaymentEvent.PaymentCompleted) captor.getValue()).tokenAmount()).isEqualTo(100L);
    }

    @Test
    void confirmPayment_alreadyCompleted_isIdempotent() {
        UUID orderId = UUID.randomUUID();
        PaymentOrder order = buildOrder(orderId, OrderStatus.COMPLETED);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        PaymentOrder result = paymentService.confirmPayment(
                PaymentProvider.CCBILL, "TXN-001", orderId, "{}");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void confirmPayment_orderNotFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.confirmPayment(
                PaymentProvider.CCBILL, "TXN-001", orderId, "{}"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── failOrder ─────────────────────────────────────────────────────────────

    @Test
    void failOrder_processingOrder_setsFailedAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        PaymentOrder order = buildOrder(orderId, OrderStatus.PROCESSING);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.failOrder(PaymentProvider.CCBILL, "TXN-002", orderId, "Card declined", "{}");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PaymentEvent.PaymentFailed.class);
    }

    @Test
    void failOrder_alreadyCompleted_isNoOp() {
        UUID orderId = UUID.randomUUID();
        PaymentOrder order = buildOrder(orderId, OrderStatus.COMPLETED);

        when(orderRepo.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.failOrder(PaymentProvider.CCBILL, "TXN-002", orderId, "late denial", "{}");

        // Status must not change; event must not be published
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_ownOrder_returnsOrder() {
        UUID userId  = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentOrder order = buildOrder(orderId, OrderStatus.COMPLETED);
        order.setUserId(userId);

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        PaymentOrder result = paymentService.getOrder(orderId, userId, false);
        assertThat(result.getId()).isEqualTo(orderId);
    }

    @Test
    void getOrder_otherUsersOrder_throws() {
        UUID userId  = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentOrder order = buildOrder(orderId, OrderStatus.COMPLETED);
        order.setUserId(UUID.randomUUID()); // belongs to a different user

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.getOrder(orderId, userId, false))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getOrder_adminCanAccessAnyOrder() {
        UUID orderId = UUID.randomUUID();
        PaymentOrder order = buildOrder(orderId, OrderStatus.COMPLETED);
        order.setUserId(UUID.randomUUID()); // belongs to a different user

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        PaymentOrder result = paymentService.getOrder(orderId, UUID.randomUUID(), true);
        assertThat(result.getId()).isEqualTo(orderId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PaymentOrder buildOrder(UUID orderId, OrderStatus status) {
        PaymentOrder o = new PaymentOrder();
        ReflectionTestUtils.setField(o, "id", orderId);
        o.setUserId(UUID.randomUUID());
        o.setProvider(PaymentProvider.CCBILL);
        o.setStatus(status);
        o.setTokenAmount(100L);
        o.setIdempotencyKey("test-key-" + orderId);
        return o;
    }
}
