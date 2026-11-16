package com.matessa.commercial.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matessa.commercial.config.CacheConfig;
import com.matessa.commercial.dto.request.CouponValidateRequest;
import com.matessa.commercial.dto.request.StockReservationRequest;
import com.matessa.commercial.dto.response.CouponValidationResponse;
import com.matessa.commercial.dto.response.StockReservationResponse;
import com.matessa.commercial.entity.Coupon;
import com.matessa.commercial.entity.Discount;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.entity.StockReservation;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.repository.StockReservationRepository;
import com.matessa.commercial.service.CouponService;
import com.matessa.commercial.service.PricingService;
import com.matessa.commercial.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds stock and a coupon use for a checkout, as an addressable {@link StockReservation}.
 *
 * <ul>
 *   <li><b>reserve</b> is idempotent by reservation id: a repeat (order-service retrying after
 *       a timeout) gets the original result back instead of taking the stock twice.</li>
 *   <li><b>release</b> is idempotent: only a HELD reservation gives stock back, once.</li>
 *   <li><b>confirm</b> (OrderPlaced) marks it CONFIRMED so the expiry job leaves it alone.</li>
 *   <li><b>expire</b>: a reservation still HELD after {@code reservation.ttl} — the order never
 *       arrived (crash, timeout, failed commit) — is released by {@code ReservationExpiryJob}.</li>
 * </ul>
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);
    private static final TypeReference<Map<Long, Integer>> LINES = new TypeReference<>() { };

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final StockReservationRepository reservations;
    private final CouponService couponService;
    private final PricingService pricingService;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ReservationServiceImpl(ProductVariantRepository variantRepository,
                                  ProductRepository productRepository,
                                  CouponRepository couponRepository,
                                  StockReservationRepository reservations,
                                  CouponService couponService,
                                  PricingService pricingService,
                                  ObjectMapper objectMapper,
                                  @Value("${reservation.ttl-minutes:60}") long ttlMinutes) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.reservations = reservations;
        this.couponService = couponService;
        this.pricingService = pricingService;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public StockReservationResponse reserve(StockReservationRequest request) {
        Optional<StockReservation> existing = reservations.lockById(request.reservationId());
        if (existing.isPresent()) {
            StockReservation r = existing.get();
            if (r.getStatus() == StockReservation.Status.RELEASED) {
                throw new ConflictException("Reservation " + r.getId() + " was already released");
            }
            return read(r.getResult(), StockReservationResponse.class); // a retry: same answer
        }

        // Duplicate lines for one variant are merged first, so the quantity checked is the
        // quantity actually wanted (two lines of 1 against a stock of 1 must fail).
        Map<Long, Integer> wanted = mergeLines(request.items());

        List<StockReservationResponse.ReservedLine> reserved = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> line : wanted.entrySet()) {
            Long variantId = line.getKey();
            int quantity = line.getValue();

            ProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ConflictException("Variant no longer available: " + variantId));
            Product product = productRepository.findById(variant.getProductId())
                    .orElseThrow(() -> new ConflictException("Variant no longer available: " + variantId));
            if (!variant.isActive() || !product.isActive()) {
                throw new ConflictException("Variant no longer available: " + variantId);
            }
            BigDecimal unitPrice = pricingService.applyBest(listPrice(variant, product), discountsFor(product));

            // The atomic guarded decrement is what actually holds the goods.
            if (variantRepository.reserveStock(variantId, quantity) == 0) {
                int available = variantRepository.findById(variantId).map(ProductVariant::getStockQuantity).orElse(0);
                throw new ConflictException("Insufficient stock for " + variant.getSku()
                        + " (available " + available + ", requested " + quantity + ")");
            }
            reserved.add(new StockReservationResponse.ReservedLine(variantId, product.getId(),
                    variant.getSku(), product.getName(), unitPrice, quantity));
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String appliedCoupon = null;
        String code = request.couponCode() == null || request.couponCode().isBlank() ? null : request.couponCode().trim();
        if (code != null) {
            discountAmount = claimCoupon(code, subtotal, request.customerKey(), request.priorCouponUses());
            appliedCoupon = code;
        }
        BigDecimal total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        StockReservationResponse response = new StockReservationResponse(reserved, subtotal, appliedCoupon, discountAmount, total);

        Instant now = Instant.now();
        StockReservation r = new StockReservation();
        r.setId(request.reservationId());
        r.setStatus(StockReservation.Status.HELD);
        r.setLines(write(wanted));
        r.setCouponCode(appliedCoupon);
        r.setCustomerKey(request.customerKey());
        r.setResult(write(response));
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        r.setExpiresAt(now.plus(ttl));
        reservations.save(r);
        if (appliedCoupon != null && request.customerKey() != null) {
            reservations.flush(); // the redemption row references the reservation
            reservations.addRedemption(appliedCoupon, request.customerKey(), r.getId());
        }

        log.info("Reserved {} ({} line(s)): subtotal {}, discount {}, total {}",
                r.getId(), reserved.size(), subtotal, discountAmount, total);
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public boolean release(String reservationId) {
        Optional<StockReservation> found = reservations.lockById(reservationId);
        if (found.isEmpty() || found.get().getStatus() != StockReservation.Status.HELD) {
            return false; // unknown (never committed) or already settled: nothing to give back
        }
        StockReservation r = found.get();
        // Mark it released and flush FIRST: the stock/coupon updates below are @Modifying
        // queries that clear the persistence context, which would silently drop a status
        // change made on the managed entity afterwards (and let a second release run).
        r.setStatus(StockReservation.Status.RELEASED);
        r.setUpdatedAt(Instant.now());
        reservations.saveAndFlush(r);
        Map<Long, Integer> lines = read(r.getLines(), LINES);
        String couponCode = r.getCouponCode();
        String id = r.getId();
        lines.forEach((variantId, quantity) -> {
            if (variantRepository.releaseStock(variantId, quantity) == 0) {
                log.warn("Release of {} found no variant {} - stock not restored", id, variantId);
            }
        });
        if (couponCode != null) {
            couponRepository.returnUse(couponCode);
            reservations.removeRedemption(id);
        }
        log.info("Released reservation {}", id);
        return true;
    }

    @Override
    @Transactional
    public void confirm(String reservationId) {
        reservations.lockById(reservationId).ifPresent(r -> {
            if (r.getStatus() == StockReservation.Status.HELD) {
                r.setStatus(StockReservation.Status.CONFIRMED);
                r.setUpdatedAt(Instant.now());
            }
        });
    }

    @Override
    @Transactional
    public void forgetCouponUse(String reservationId) {
        reservations.removeRedemption(reservationId);
    }

    /**
     * Validates the coupon and claims one use, under a lock on the coupon row so the
     * per-customer count and the new redemption are one atomic step.
     */
    private BigDecimal claimCoupon(String code, BigDecimal subtotal, String customerKey, Integer priorUses) {
        CouponValidationResponse validation = couponService.validate(new CouponValidateRequest(code, subtotal));
        if (!validation.valid()) {
            throw new ConflictException("Coupon not applicable: " + validation.message());
        }
        Coupon coupon = couponRepository.lockByCode(code)
                .orElseThrow(() -> new ConflictException("Coupon not applicable: Coupon not found"));
        Integer perUserLimit = coupon.getPerUserLimit();
        if (perUserLimit != null && customerKey != null) {
            long recorded = reservations.countRedemptions(code, customerKey);
            long used = Math.max(recorded, priorUses == null ? 0 : priorUses);
            if (used >= perUserLimit) {
                throw new ConflictException("Coupon not applicable: you have already used this code "
                        + used + " time(s) and the limit is " + perUserLimit);
            }
        }
        if (couponRepository.claimUse(code) == 0) {
            throw new ConflictException("Coupon not applicable: usage limit reached");
        }
        BigDecimal amount = validation.discountAmount();
        return amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** Sums quantities per variant, ascending id so concurrent multi-line orders lock in one order. */
    private Map<Long, Integer> mergeLines(List<StockReservationRequest.Line> lines) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        lines.stream()
                .sorted(Comparator.comparing(StockReservationRequest.Line::productVariantId))
                .forEach(line -> merged.merge(line.productVariantId(), line.quantity(), Integer::sum));
        return merged;
    }

    private List<Discount> discountsFor(Product product) {
        return pricingService.activeDiscountsFor(product.getId(), product.getCategoryId());
    }

    private BigDecimal listPrice(ProductVariant variant, Product product) {
        return variant.getPriceOverride() != null ? variant.getPriceOverride() : product.getBasePrice();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise reservation", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read reservation", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read reservation", e);
        }
    }
}
