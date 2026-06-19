package com.platform.payments.api;

import com.platform.payments.api.dto.CreateOrderRequest;
import com.platform.payments.api.dto.OrderResponse;
import com.platform.payments.config.TokenPackageProperties;
import com.platform.payments.domain.PaymentOrder;
import com.platform.payments.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TokenPackageProperties packageProperties;

    // Public — shown on the buy-tokens page before the user is redirected to the provider
    @GetMapping("/packages")
    public ResponseEntity<List<TokenPackageProperties.TokenPackage>> listPackages() {
        return ResponseEntity.ok(packageProperties.getTokenPackages());
    }

    // Create a purchase order; response includes redirectUrl — client must redirect user there
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid CreateOrderRequest req,
            Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        PaymentOrder order = paymentService.createOrder(userId, req.packageId(), req.provider());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/orders/me")
    public ResponseEntity<Page<OrderResponse>> myOrders(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = UUID.fromString(auth.getName());
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<OrderResponse> result = paymentService.getUserOrders(userId, pageable)
                .map(OrderResponse::from);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID orderId,
            Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        PaymentOrder order = paymentService.getOrder(orderId, userId, isAdmin);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // ── Admin / ops endpoints ──────────────────────────────────────────────────

    // Manually complete an order (for testing or manual payment reconciliation)
    @PostMapping("/admin/orders/{orderId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> adminComplete(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "MANUAL") String providerOrderId) {

        PaymentOrder order = paymentService.confirmPayment(null, providerOrderId, orderId, "admin-manual");
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // Manually fail an order (ops use-case: mark a stuck PROCESSING order as FAILED)
    @PostMapping("/admin/orders/{orderId}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> adminFail(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "Admin action") String reason) {

        PaymentOrder order = paymentService.failOrder(null, "MANUAL", orderId, reason, "admin-manual");
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
