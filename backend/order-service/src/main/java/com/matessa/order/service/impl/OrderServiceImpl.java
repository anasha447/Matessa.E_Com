package com.matessa.order.service.impl;

import com.matessa.order.client.CommercialClient;
import com.matessa.order.client.ReservationRequest;
import com.matessa.order.client.ReservationResult;
import com.matessa.order.dto.request.CartItemRequest;
import com.matessa.order.dto.request.CheckoutRequest;
import com.matessa.order.dto.request.GuestCheckoutRequest;
import com.matessa.order.dto.request.ShippingAddressRequest;
import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.dto.response.OrderItemResponse;
import com.matessa.order.dto.response.OrderResponse;
import com.matessa.order.dto.response.OrderStatusHistoryResponse;
import com.matessa.order.dto.response.OrderSummaryResponse;
import com.matessa.order.entity.Cart;
import com.matessa.order.entity.CartItem;
import com.matessa.order.entity.Order;
import com.matessa.order.entity.OrderItem;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.OrderStatusHistory;
import com.matessa.order.entity.OutboxEvent;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.entity.ProcessedEvent;
import com.matessa.order.entity.ShippingStatus;
import com.matessa.order.exception.BadRequestException;
import com.matessa.order.exception.ConflictException;
import com.matessa.order.exception.ResourceNotFoundException;
import com.matessa.order.messaging.KafkaTopics;
import com.matessa.order.messaging.OrderCancelledEvent;
import com.matessa.order.messaging.OrderPlacedEvent;
import com.matessa.order.messaging.OrderRejectedEvent;
import com.matessa.order.repository.CartItemRepository;
import com.matessa.order.repository.CartRepository;
import com.matessa.order.repository.OrderItemRepository;
import com.matessa.order.repository.OrderRepository;
import com.matessa.order.repository.OrderStatusHistoryRepository;
import com.matessa.order.repository.OutboxRepository;
import com.matessa.order.repository.ProcessedEventRepository;
import com.matessa.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.matessa.order.service.PaymentResult;
import com.matessa.order.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    // Customer-facing order id: "MA" + ddMMHHmmss in Indian wall-clock time,
    // e.g. MA1407143207 for 14 Jul 14:32:07 IST. Seconds keep same-minute orders
    // distinct; the existsByOrderNumber retry loop covers same-second collisions
    // (and the rare year-later repeat) by rolling to the next second.
    private static final DateTimeFormatter ORDER_NUMBER_FORMAT =
            DateTimeFormatter.ofPattern("ddMMHHmmss").withZone(ZoneId.of("Asia/Kolkata"));

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final CommercialClient commercialClient;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(CartRepository cartRepository,
                            CartItemRepository cartItemRepository,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            OrderStatusHistoryRepository statusHistoryRepository,
                            OutboxRepository outboxRepository,
                            ProcessedEventRepository processedEventRepository,
                            CommercialClient commercialClient,
                            PaymentService paymentService,
                            ObjectMapper objectMapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.outboxRepository = outboxRepository;
        this.processedEventRepository = processedEventRepository;
        this.commercialClient = commercialClient;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request, String idempotencyKey) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Cart is empty"));
        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }
        List<CartItemRequest> lines = cartItems.stream()
                .map(ci -> new CartItemRequest(ci.getProductVariantId(), ci.getQuantity()))
                .toList();
        OrderResponse response = createOrder(userId, null, lines, request.shippingAddress(),
                request.paymentMethod(), request.couponCode(), request.shippingMethod(), idempotencyKey);
        cartItemRepository.deleteByCartId(cart.getId());
        return response;
    }

    @Override
    @Transactional
    public OrderResponse guestCheckout(GuestCheckoutRequest request, String idempotencyKey) {
        // Guests have no server cart — the lines come from the (client-side) guest cart and
        // are re-priced authoritatively here, exactly like a registered checkout.
        return createOrder(null, request.guestEmail(), request.items(), request.shippingAddress(),
                request.paymentMethod(), request.couponCode(), request.shippingMethod(), idempotencyKey);
    }

    /** Shared order-placement core for registered and guest checkout. Exactly one of
     *  {@code userId}/{@code guestEmail} is set. Re-prices each line, validates stock,
     *  charges (mock), and persists the order + items + outbox event in one transaction.
     *  {@code idempotencyKey} (nullable) dedupes retried submits. */
    private OrderResponse createOrder(Long userId, String guestEmail, List<CartItemRequest> lines,
                                      ShippingAddressRequest shippingAddress, PaymentMethod paymentMethod,
                                      String couponCode, String shippingMethod, String idempotencyKey) {
        if (lines.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        // 0) Idempotency fast-return: a repeat with the same key hands back the original order —
        //    no re-price, no second charge, no duplicate row.
        //
        //    The key is client-supplied and its unique index is global, so the result MUST be
        //    re-checked against the caller before the order is handed back. Without that check,
        //    anyone presenting someone else's key — including an unauthenticated guest-checkout
        //    caller — receives that order in full: totals, line items, the shipping-address
        //    snapshot and the payment transaction id.
        //
        //    A key that exists but belongs to someone else gets the same 409 as a genuine
        //    duplicate-key collision further down, so the response cannot be used to probe
        //    whether a given key exists.
        String idemKey = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
        if (idemKey != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idemKey);
            if (existing.isPresent()) {
                Order prior = existing.get();
                if (!isSameRequester(prior, userId, guestEmail)) {
                    throw new ConflictException("Duplicate order request — idempotency key already used.");
                }
                return toResponse(prior, orderItemRepository.findByOrderIdOrderByIdAsc(prior.getId()));
            }
        }

        // 1) How many times this customer has already used the coupon. perUserLimit was
        //    persisted and handed to clients but never actually enforced - the repository
        //    method for this count existed with no callers. The count has to come from here
        //    because order-service owns the order history; the limit it is compared against
        //    belongs to commercial-service. So the count travels with the reservation and the
        //    comparison happens there, inside the same transaction that claims the use.
        //    Guests have no history to count, so the limit does not apply to them.
        String normalisedCoupon = couponCode == null || couponCode.isBlank() ? null : couponCode.trim();
        Integer priorCouponUses = (userId != null && normalisedCoupon != null)
                ? (int) orderRepository.countByUserIdAndCouponCode(userId, normalisedCoupon)
                : null;

        // 2) Reserve stock and claim the coupon use BEFORE paying, in one transaction over on
        //    commercial-service, which also prices the basket and returns what it reserved at.
        //
        //    The previous shape read stock here, charged the card, and left the actual
        //    decrement to commercial-service consuming OrderPlaced up to two seconds later.
        //    Nothing held the stock across that gap, so every concurrent buyer of the last
        //    unit passed the read, every one was charged, and all but one order was cancelled
        //    afterwards. Reserving first turns that into a 409 before any money moves.
        //
        //    It also replaces one Feign round trip per line with one for the whole basket.
        ReservationRequest reservationRequest = new ReservationRequest(
                lines.stream()
                        .map(line -> new ReservationRequest.Line(line.productVariantId(), line.quantity()))
                        .toList(),
                normalisedCoupon,
                priorCouponUses);
        ReservationResult reservation = commercialClient.reserve(reservationRequest);
        // From here on the goods are already held, so every exit has to hand the
        // reservation back: a declined card, a lost idempotency-key race, or a database
        // failure part-way through persisting. commercial-service holds the reservation in
        // its own database, so this service rolling back does not undo it.
        //
        // Not covered: the process dying between the reservation and this catch. That
        // leaves stock held with no order behind it, and the durable fix is a
        // reconciliation job that expires reservations - noted, not built.
        try {
            List<OrderItem> orderItems = new ArrayList<>();
            List<OrderPlacedEvent.Line> eventLines = new ArrayList<>();
            for (ReservationResult.Line line : reservation.lines()) {
                OrderItem orderItem = new OrderItem();
                orderItem.setProductVariantId(line.productVariantId());
                orderItem.setProductNameSnapshot(line.productName());
                orderItem.setSkuSnapshot(line.sku());
                orderItem.setUnitPriceSnapshot(line.unitPrice());
                orderItem.setQuantity(line.quantity());
                orderItems.add(orderItem);
                eventLines.add(new OrderPlacedEvent.Line(line.productVariantId(), line.quantity()));
            }
            BigDecimal subtotal = reservation.subtotal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal discountAmount = reservation.discountAmount() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : reservation.discountAmount().setScale(2, RoundingMode.HALF_UP);
            String appliedCoupon = reservation.couponCode();
            BigDecimal total = reservation.total().setScale(2, RoundingMode.HALF_UP);

            // 3) payment (mock). A decline is now a real failure rather than a PENDING order:
            //    the stock is already held, so leaving it held behind an order nobody paid for
            //    would quietly make it unsellable.
            PaymentResult payment = paymentService.charge(paymentMethod, total);
            if (payment.status() == PaymentStatus.FAILED) {
                throw new BadRequestException("Payment was declined — the order was not placed.");
            }
            OrderStatus status =
                    payment.status() == PaymentStatus.COMPLETED ? OrderStatus.PAID : OrderStatus.PENDING;

            // 4) persist the order + items + outbox event in one transaction
            Order order = new Order();
            order.setUserId(userId);
            order.setGuestEmail(guestEmail);
            order.setIdempotencyKey(idemKey);
            order.setOrderNumber(generateOrderNumber());
            order.setStatus(status);
            order.setSubtotal(subtotal);
            order.setDiscountAmount(discountAmount);
            order.setTotal(total);
            order.setCouponCode(appliedCoupon);
            order.setShippingAddressSnapshot(toJson(shippingAddress));
            order.setShippingState(normalizeState(shippingAddress.state()));
            order.setPaymentStatus(payment.status());
            order.setPaymentMethod(paymentMethod);
            order.setTransactionId(payment.transactionId());
            order.setShippingStatus(ShippingStatus.PENDING);
            order.setShippingMethod(shippingMethod);
            Order saved;
            try {
                // saveAndFlush forces the unique idempotency_key constraint to fire now: if a
                // concurrent request with the same key committed first, this order loses the race.
                saved = orderRepository.saveAndFlush(order);
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("Duplicate order request — idempotency key already used.");
            }

            for (OrderItem orderItem : orderItems) {
                orderItem.setOrderId(saved.getId());
                orderItemRepository.save(orderItem);
            }
            recordHistory(saved, null, status, "Order placed", userId);

            OrderPlacedEvent event = new OrderPlacedEvent(UUID.randomUUID().toString(), saved.getOrderNumber(),
                    userId, appliedCoupon, eventLines, Instant.now());
            enqueue(saved.getOrderNumber(), KafkaTopics.ORDER_PLACED, event);

            return toResponse(saved, orderItems);
        } catch (RuntimeException ex) {
            releaseQuietly(reservationRequest);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listOwn(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId, boolean staff) {
        Order order = findVisibleOrder(orderId, userId, staff);
        return toResponse(order, orderItemRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(Long userId, String orderNumber, boolean staff) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
        if (!staff && !Objects.equals(order.getUserId(), userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderNumber);   // don't reveal others' orders
        }
        return toResponse(order, orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse trackGuestOrder(String orderNumber, String email) {
        // Public lookup: require BOTH the order number and the email it was placed with, so
        // the number alone can't be used to enumerate orders. Guest orders only.
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> o.getGuestEmail() != null && o.getGuestEmail().equalsIgnoreCase(email.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("No order matches that number and email."));
        return toResponse(order, orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()));
    }

    @Override
    @Transactional
    public OrderResponse cancel(Long userId, Long orderId, boolean staff) {
        Order order = findVisibleOrder(orderId, userId, staff);
        applyCancellation(order, staff ? null : userId, staff ? "Cancelled by staff" : "Cancelled by customer", true);
        return toResponse(order, orderItemRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> history(Long userId, Long orderId, boolean staff) {
        findVisibleOrder(orderId, userId, staff);   // visibility check
        return statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId).stream()
                .map(this::toHistoryResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listAll(OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toSummary);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus target, String note, Long actor) {
        Order order = findOrder(orderId);
        if (target == OrderStatus.CANCELLED) {
            applyCancellation(order, actor, note != null ? note : "Cancelled by staff", true);
            return toResponse(order, orderItemRepository.findByOrderIdOrderByIdAsc(orderId));
        }
        assertTransition(order.getStatus(), target);
        OrderStatus from = order.getStatus();
        order.setStatus(target);
        if (target == OrderStatus.PAID && order.getPaymentStatus() == PaymentStatus.PENDING) {
            order.setPaymentStatus(PaymentStatus.COMPLETED);   // e.g. COD collected
        }
        if (target == OrderStatus.SHIPPED) {
            order.setShippingStatus(ShippingStatus.SHIPPED);
        }
        if (target == OrderStatus.DELIVERED) {
            order.setShippingStatus(ShippingStatus.DELIVERED);
        }
        orderRepository.save(order);
        recordHistory(order, from, target, note, actor);
        return toResponse(order, orderItemRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    @Override
    @Transactional
    public OrderResponse updateShipping(Long orderId, String shippingMethod, String trackingNumber, Long actor) {
        Order order = findOrder(orderId);
        order.setShippingMethod(shippingMethod);
        order.setTrackingNumber(trackingNumber);
        orderRepository.save(order);
        return toResponse(order, orderItemRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    @Override
    @Transactional
    public void handleRejection(OrderRejectedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;   // idempotent
        }
        Order order = orderRepository.findByOrderNumber(event.orderNumber()).orElse(null);
        if (order != null
                && order.getStatus() != OrderStatus.CANCELLED
                && order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.DELIVERED) {
            // stock was never decremented (the order was rejected) -> do not emit OrderCancelled
            applyCancellation(order, null, "Auto-cancelled: " + event.reason(), false);
        }
        markProcessed(event.eventId());
    }

    // ---- internals ----

    private void applyCancellation(Order order, Long actor, String note, boolean releaseStock) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("Order is already cancelled");
        }
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new ConflictException("Cannot cancel an order that has already shipped");
        }
        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);   // mock refund
        }
        orderRepository.save(order);
        recordHistory(order, from, OrderStatus.CANCELLED, note, actor);

        if (releaseStock) {
            List<OrderCancelledEvent.Line> lines = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
                    .map(item -> new OrderCancelledEvent.Line(item.getProductVariantId(), item.getQuantity())).toList();
            OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID().toString(), order.getOrderNumber(),
                    order.getCouponCode(), lines, Instant.now());
            enqueue(order.getOrderNumber(), KafkaTopics.ORDER_CANCELLED, event);
        }
    }

    private void assertTransition(OrderStatus from, OrderStatus to) {
        boolean ok = switch (from) {
            case PENDING -> to == OrderStatus.PAID;
            case PAID -> to == OrderStatus.SHIPPED;
            case SHIPPED -> to == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!ok) {
            throw new BadRequestException("Illegal status transition: " + from + " -> " + to);
        }
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private Order findVisibleOrder(Long id, Long userId, boolean staff) {
        Order order = findOrder(id);
        // Objects.equals is null-safe: a guest order (userId null) is visible to staff only.
        if (!staff && !Objects.equals(order.getUserId(), userId)) {
            throw new ResourceNotFoundException("Order not found: " + id);   // don't reveal others' orders
        }
        return order;
    }

    private void recordHistory(Order order, OrderStatus from, OrderStatus to, String note, Long changedBy) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setNote(note);
        history.setChangedBy(changedBy);
        statusHistoryRepository.save(history);
    }

    private void enqueue(String aggregateId, String eventType, Object payload) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("Order");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outboxRepository.save(outbox);
    }

    private void markProcessed(String eventId) {
        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private String generateOrderNumber() {
        String candidate;
        do {
            candidate = "MA" + ORDER_NUMBER_FORMAT.format(Instant.now());
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }

    /** Trimmed state for the queryable column; blank → null (counted as "Other" in analytics). */
    private String normalizeState(String state) {
        if (state == null) return null;
        String trimmed = state.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize order payload", ex);
        }
    }

    private OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getOrderNumber(), order.getUserId(), order.getStatus(),
                order.getTotal(), order.getPaymentStatus(), order.getShippingStatus(), order.getCreatedAt());
    }

    private OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory history) {
        return new OrderStatusHistoryResponse(history.getId(), history.getFromStatus(), history.getToStatus(),
                history.getNote(), history.getChangedBy(), history.getChangedAt());
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> lines = items.stream().map(item -> new OrderItemResponse(
                item.getId(), item.getProductVariantId(), item.getProductNameSnapshot(), item.getSkuSnapshot(),
                item.getUnitPriceSnapshot(), item.getQuantity(),
                item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP)
        )).toList();
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getUserId(), order.getStatus(),
                order.getSubtotal(), order.getDiscountAmount(), order.getTotal(), order.getCouponCode(),
                order.getShippingAddressSnapshot(), order.getPaymentStatus(), order.getPaymentMethod(),
                order.getTransactionId(), order.getShippingStatus(), order.getShippingMethod(),
                order.getTrackingNumber(), lines, order.getCreatedAt());
    }

    /** True when {@code order} was placed by the same requester that is now presenting its
     *  idempotency key. Exactly one of {@code userId}/{@code guestEmail} is set on a checkout,
     *  and the stored order likewise carries exactly one of them, so a registered caller can
     *  never match a guest order and vice versa. Guest emails are compared case-insensitively
     *  because the client controls their casing. */
    private boolean isSameRequester(Order order, Long userId, String guestEmail) {
        if (userId != null) {
            return userId.equals(order.getUserId());
        }
        if (guestEmail != null && order.getGuestEmail() != null) {
            return guestEmail.trim().equalsIgnoreCase(order.getGuestEmail().trim());
        }
        return false;
    }


    /**
     * Hands a reservation back on a path that is already failing.
     *
     * <p>Never throws: the caller is in the middle of reporting another error to the customer
     * and must not be derailed by the compensation. A failure here means stock stays held,
     * which is logged loudly by the Feign fallback.
     */
    private void releaseQuietly(ReservationRequest reservationRequest) {
        try {
            commercialClient.releaseReservation(reservationRequest);
        } catch (RuntimeException ex) {
            log.error("Failed to release a reservation after a failed checkout; stock may stay held: {}",
                    ex.toString());
        }
    }
}
