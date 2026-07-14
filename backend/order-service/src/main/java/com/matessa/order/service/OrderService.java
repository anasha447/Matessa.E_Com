package com.matessa.order.service;

import com.matessa.order.dto.request.CheckoutRequest;
import com.matessa.order.dto.request.GuestCheckoutRequest;
import com.matessa.order.dto.response.OrderResponse;
import com.matessa.order.dto.response.OrderStatusHistoryResponse;
import com.matessa.order.dto.response.OrderSummaryResponse;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.messaging.OrderRejectedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {

    /** Place an order from the caller's cart. {@code idempotencyKey} (nullable) makes a repeated
     *  submit return the same order instead of creating a duplicate. Returns the order, or 409. */
    OrderResponse checkout(Long userId, CheckoutRequest request, String idempotencyKey);

    /** Place an order without an account, from client-supplied (re-priced) lines. */
    OrderResponse guestCheckout(GuestCheckoutRequest request, String idempotencyKey);

    Page<OrderSummaryResponse> listOwn(Long userId, Pageable pageable);

    OrderResponse getOrder(Long userId, Long orderId, boolean staff);

    /** Fetch by opaque order number (used for /orders/<number> URLs). Owner or staff only. */
    OrderResponse getOrderByNumber(Long userId, String orderNumber, boolean staff);

    /** Public guest-order lookup: matches order number + the email it was placed with. */
    OrderResponse trackGuestOrder(String orderNumber, String email);

    OrderResponse cancel(Long userId, Long orderId, boolean staff);

    List<OrderStatusHistoryResponse> history(Long userId, Long orderId, boolean staff);

    // ---- staff ----

    Page<OrderSummaryResponse> listAll(OrderStatus status, Pageable pageable);

    OrderResponse updateStatus(Long orderId, OrderStatus target, String note, Long actor);

    OrderResponse updateShipping(Long orderId, String shippingMethod, String trackingNumber, Long actor);

    // ---- async (saga compensation) ----

    /** Commercial rejected the order (stock unavailable) → cancel it. Idempotent. */
    void handleRejection(OrderRejectedEvent event);
}
