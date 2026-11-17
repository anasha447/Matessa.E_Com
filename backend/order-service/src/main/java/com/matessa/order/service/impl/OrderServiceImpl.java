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

    // Order number date part (Indian wall-clock day) and its random suffix alphabet.
    private static final DateTimeFormatter ORDER_NUMBER_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Asia/Kolkata"));
    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

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
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final IdempotencyStore idempotency;

    public OrderServiceImpl(CartRepository cartRepository,
                            CartItemRepository cartItemRepository,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            OrderStatusHistoryRepository statusHistoryRepository,
                            OutboxRepository outboxRepository,
                            ProcessedEventRepository processedEventRepository,
                            CommercialClient commercialClient,
                            PaymentService paymentService,
                            ObjectMapper objectMapper,
                            org.springframework.transaction.support.TransactionTemplate transactions,
                            IdempotencyStore idempotency) {
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
        this.transactions = transactions;
        this.idempotency = idempotency;
    }

    /**
     * Not @Transactional on purpose: checkout talks to commercial-service (reserve) and the
     * payment provider, and must not hold a database connection or transaction open across
     * those calls. Each local step runs in its own short transaction (see {@link #createOrder}).
     */
    @Override
    public OrderResponse checkout(Long userId, CheckoutRequest request, String idempotencyKey) {
        // A retry of a checkout that already succeeded (the response was lost, the shopper hit
        // retry) arrives with the cart already emptied by that order. Answer it from the order
        // the key produced: its items are the basket that was hashed, so the normal key check
        // below still returns that order for the same request and 422 for a different one.
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() <= 80) {
            Optional<Long> finished = idempotency.finishedOrder(key, "u:" + userId);
            if (finished.isPresent()) {
                List<CartItemRequest> placed = orderItemRepository.findByOrderIdOrderByIdAsc(finished.get()).stream()
                        .map(item -> new CartItemRequest(item.getProductVariantId(), item.getQuantity()))
                        .toList();
                if (!placed.isEmpty()) {
                    return createOrder(userId, null, placed, request.shippingAddress(), request.paymentMethod(),
                            request.couponCode(), request.shippingMethod(), key, true);
                }
            }
        }
        List<CartItemRequest> lines = transactions.execute(tx -> {
            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new BadRequestException("Cart is empty"));
            List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
            return cartItems.stream()
                    .map(ci -> new CartItemRequest(ci.getProductVariantId(), ci.getQuantity()))
                    .toList();
        });
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }
        return createOrder(userId, null, lines, request.shippingAddress(), request.paymentMethod(),
                request.couponCode(), request.shippingMethod(), idempotencyKey, true);
    }

    @Override
    public OrderResponse guestCheckout(GuestCheckoutRequest request, String idempotencyKey) {
        // Guests have no server cart — the lines come from the (client-side) guest cart and
        // are re-priced authoritatively here, exactly like a registered checkout.
        return createOrder(null, request.guestEmail().trim().toLowerCase(java.util.Locale.ROOT), request.items(),
                request.shippingAddress(), request.paymentMethod(), request.couponCode(),
                request.shippingMethod(), idempotencyKey, false);
    }

    /**
     * Shared order placement for registered and guest checkout. Exactly one of
     * {@code userId}/{@code guestEmail} is set.
     *
     * <ol>
     *   <li>Idempotency: claim the key (or return the order it already produced).</li>
     *   <li>Reserve stock + the coupon use on commercial-service, under a reservation id we
     *       generate — so a timed-out call can still be released, and a retry is harmless.</li>
     *   <li>Take payment.</li>
     *   <li>Persist order + items + outbox event + idempotency result in ONE short transaction.</li>
     * </ol>
     * Any failure after step 2 — a declined card, a validation error, a failed commit — releases
     * the reservation and frees the idempotency key. If even that release cannot be delivered,
     * commercial-service's expiry job returns the stock later.
     */
    private OrderResponse createOrder(Long userId, String guestEmail, List<CartItemRequest> lines,
                                      ShippingAddressRequest shippingAddress, PaymentMethod paymentMethod,
                                      String couponCode, String shippingMethod, String idempotencyKey,
                                      boolean clearCart) {
        if (lines.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }
        String customerKey = userId != null ? "u:" + userId : "e:" + guestEmail;
        String normalisedCoupon = couponCode == null || couponCode.isBlank() ? null : couponCode.trim();

        // 1) Idempotency.
        String idemKey = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
        if (idemKey != null) {
            if (idemKey.length() > 80) {
                throw new BadRequestException("Idempotency-Key is too long");
            }
            String hash = requestHash(lines, shippingAddress, paymentMethod, normalisedCoupon, shippingMethod);
            Optional<Long> done = idempotency.begin(idemKey, customerKey, hash);
            if (done.isPresent()) {
                Order prior = orderRepository.findById(done.get())
                        .orElseThrow(() -> new ConflictException("Duplicate order request — idempotency key already used."));
                return toResponse(prior, orderItemRepository.findByOrderIdOrderByIdAsc(prior.getId()));
            }
        }

        // Prior coupon uses from order history (orders placed before commercial-service kept
        // its own redemption records); commercial compares under a lock on the coupon row.
        Integer priorCouponUses = null;
        if (normalisedCoupon != null) {
            priorCouponUses = userId != null
                    ? (int) orderRepository.countByUserIdAndCouponCode(userId, normalisedCoupon)
                    : (int) orderRepository.countGuestCouponUses(guestEmail, normalisedCoupon);
        }

        // 2) Reserve. The id is ours, so even a reserve call that times out after commercial
        //    committed it can be released by id.
        String reservationId = UUID.randomUUID().toString();
        ReservationRequest reservationRequest = new ReservationRequest(
                reservationId,
                lines.stream().map(line -> new ReservationRequest.Line(line.productVariantId(), line.quantity())).toList(),
                normalisedCoupon,
                customerKey,
                priorCouponUses);
        ReservationResult reservation;
        try {
            reservation = commercialClient.reserve(reservationRequest);
        } catch (RuntimeException ex) {
            if (!(ex instanceof ConflictException)) {
                releaseQuietly(reservationId); // timed out / unreachable: it may still have been made
            }
            abandonQuietly(idemKey);
            throw ex;
        }

        try {
            // 3) Payment. A decline is a real failure: the stock is held, so it must go back.
            BigDecimal total = reservation.total().setScale(2, RoundingMode.HALF_UP);
            PaymentResult payment = paymentService.charge(paymentMethod, total);
            if (payment.status() == PaymentStatus.FAILED) {
                throw new BadRequestException("Payment was declined — the order was not placed.");
            }

            // 4) Persist everything in one short transaction.
            OrderResponse response = transactions.execute(tx ->
                    persistOrder(userId, guestEmail, reservation, reservationId, payment, paymentMethod,
                            shippingAddress, shippingMethod, idemKey, clearCart));
            log.info("Order {} placed (reservation {})", response.orderNumber(), reservationId);
            return response;
        } catch (RuntimeException ex) {
            releaseQuietly(reservationId);
            abandonQuietly(idemKey);
            throw ex;
        }
    }

    private OrderResponse persistOrder(Long userId, String guestEmail, ReservationResult reservation,
                                       String reservationId, PaymentResult payment, PaymentMethod paymentMethod,
                                       ShippingAddressRequest shippingAddress, String shippingMethod,
                                       String idemKey, boolean clearCart) {
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
        OrderStatus status = payment.status() == PaymentStatus.COMPLETED ? OrderStatus.PAID : OrderStatus.PENDING;

        Order order = new Order();
        order.setUserId(userId);
        order.setGuestEmail(guestEmail);
        order.setIdempotencyKey(idemKey);
        order.setReservationId(reservationId);
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
                userId, appliedCoupon, eventLines, Instant.now(), reservationId);
        enqueue(saved.getOrderNumber(), KafkaTopics.ORDER_PLACED, event);

        if (idemKey != null) {
            idempotency.complete(idemKey, saved.getId());
        }
        if (clearCart && userId != null) {
            cartRepository.findByUserId(userId).ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
        }
        return toResponse(saved, orderItems);
    }

    /** SHA-256 of a canonical form of the purchase: same basket + details = same hash. */
    private String requestHash(List<CartItemRequest> lines, ShippingAddressRequest address, PaymentMethod method,
                               String coupon, String shippingMethod) {
        java.util.TreeMap<Long, Integer> merged = new java.util.TreeMap<>();
        lines.forEach(l -> merged.merge(l.productVariantId(), l.quantity(), Integer::sum));
        String canonical = merged + "|" + toJson(address) + "|" + method + "|" + (coupon == null ? "" : coupon)
                + "|" + (shippingMethod == null ? "" : shippingMethod);
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void abandonQuietly(String idemKey) {
        if (idemKey == null) {
            return;
        }
        try {
            idempotency.abandon(idemKey);
        } catch (RuntimeException ex) {
            log.warn("Could not free idempotency key after a failed checkout: {}", ex.toString());
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
                    order.getCouponCode(), lines, Instant.now(), order.getReservationId());
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

    /**
     * Customer-facing order number: store prefix + yyMMdd + 6 random Crockford base-32 characters,
     * e.g. MA261116-7K3QXD. The old number was the timestamp to the second, so anyone could
     * enumerate other people's orders (guest tracking needs only number + email). 30 random
     * bits per day make guessing impractical; the loop covers the rare collision.
     */
    private String generateOrderNumber() {
        String store = com.matessa.order.tenant.TenantContext.getTenantId();
        String prefix = store == null || store.length() < 2 ? "MA" : store.substring(0, 2).toUpperCase(java.util.Locale.ROOT);
        String day = ORDER_NUMBER_FORMAT.format(Instant.now());
        String candidate;
        do {
            StringBuilder random = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                random.append(CROCKFORD.charAt(RANDOM.nextInt(CROCKFORD.length())));
            }
            candidate = prefix + day + "-" + random;
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



    /**
     * Hands a reservation back on a path that is already failing.
     *
     * <p>Never throws: the caller is in the middle of reporting another error to the customer
     * and must not be derailed by the compensation. A failure here means stock stays held,
     * which is logged loudly by the Feign fallback.
     */
    private void releaseQuietly(String reservationId) {
        try {
            commercialClient.releaseReservation(reservationId);
        } catch (RuntimeException ex) {
            log.error("Failed to release reservation {} after a failed checkout; commercial-service's expiry "
                    + "job will return the stock: {}", reservationId, ex.toString());
        }
    }
}
