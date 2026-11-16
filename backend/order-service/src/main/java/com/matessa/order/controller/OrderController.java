package com.matessa.order.controller;

import com.matessa.order.dto.request.CheckoutRequest;
import com.matessa.order.dto.request.GuestCheckoutRequest;
import com.matessa.order.dto.request.UpdateOrderStatusRequest;
import com.matessa.order.dto.request.UpdateShippingRequest;
import com.matessa.order.dto.response.OrderResponse;
import com.matessa.order.dto.response.OrderStatusHistoryResponse;
import com.matessa.order.dto.response.OrderSummaryResponse;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Orders. Customers see and cancel only their own; staff (MANAGER/ADMIN) list all and
 * advance status/shipping. Ownership is enforced in the service; staff-only routes use
 * @PreAuthorize.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final com.matessa.order.service.PaymentService paymentService;

    public OrderController(OrderService orderService, com.matessa.order.service.PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@AuthenticationPrincipal Long userId, @Valid @RequestBody CheckoutRequest request,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return orderService.checkout(userId, request, idempotencyKey);
    }

    /** Guest checkout — no account required (public). Lines are supplied inline. */
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse guestCheckout(@Valid @RequestBody GuestCheckoutRequest request,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return orderService.guestCheckout(request, idempotencyKey);
    }

    /** Public: the payment methods this store accepts right now (COD-only until a real provider). */
    @GetMapping("/payment-methods")
    public java.util.Map<String, List<com.matessa.order.entity.PaymentMethod>> paymentMethods() {
        return java.util.Map.of("methods", paymentService.supportedMethods());
    }

    /** Public guest-order tracking: order number + the email it was placed with. */
    @GetMapping("/track")
    public OrderResponse track(@RequestParam String number, @RequestParam String email) {
        return orderService.trackGuestOrder(number, email);
    }

    @GetMapping
    public Page<OrderSummaryResponse> listOwn(@AuthenticationPrincipal Long userId, Pageable pageable) {
        return orderService.listOwn(userId, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id, Authentication authentication) {
        return orderService.getOrder(userId, id, isStaff(authentication));
    }

    /** Fetch by opaque order number — backs the /orders/<number> and /admin/orders/<number> URLs. */
    @GetMapping("/by-number/{orderNumber}")
    public OrderResponse getByNumber(@AuthenticationPrincipal Long userId, @PathVariable String orderNumber,
                                     Authentication authentication) {
        return orderService.getOrderByNumber(userId, orderNumber, isStaff(authentication));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Long userId, @PathVariable Long id, Authentication authentication) {
        return orderService.cancel(userId, id, isStaff(authentication));
    }

    @GetMapping("/{id}/history")
    public List<OrderStatusHistoryResponse> history(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                    Authentication authentication) {
        return orderService.history(userId, id, isStaff(authentication));
    }

    // ---- staff ----

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public Page<OrderSummaryResponse> listAll(@RequestParam(required = false) OrderStatus status, Pageable pageable) {
        return orderService.listAll(status, pageable);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public OrderResponse updateStatus(@PathVariable Long id, @AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.note(), userId);
    }

    @PutMapping("/{id}/shipping")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public OrderResponse updateShipping(@PathVariable Long id, @AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody UpdateShippingRequest request) {
        return orderService.updateShipping(id, request.shippingMethod(), request.trackingNumber(), userId);
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_MANAGER") || role.equals("ROLE_ADMIN"));
    }
}
