package com.matessa.commercial.service.impl;

import com.matessa.commercial.config.CacheConfig;
import com.matessa.commercial.dto.request.CouponValidateRequest;
import com.matessa.commercial.dto.request.StockReservationRequest;
import com.matessa.commercial.dto.response.CouponValidationResponse;
import com.matessa.commercial.dto.response.StockReservationResponse;
import com.matessa.commercial.entity.Coupon;
import com.matessa.commercial.entity.Discount;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.service.CouponService;
import com.matessa.commercial.service.PricingService;
import com.matessa.commercial.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final CouponService couponService;
    private final PricingService pricingService;

    public ReservationServiceImpl(ProductVariantRepository variantRepository,
                                  ProductRepository productRepository,
                                  CouponRepository couponRepository,
                                  CouponService couponService,
                                  PricingService pricingService) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.couponService = couponService;
        this.pricingService = pricingService;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public StockReservationResponse reserve(StockReservationRequest request) {
        // Duplicate lines for one variant used to be validated independently against the full
        // stock figure, so two lines of quantity 1 both passed against a stock of 1. Merging
        // first means the quantity checked is the quantity actually wanted.
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

            // Price BEFORE taking the stock, so the figure returned is the one being committed
            // to. The atomic decrement below is what actually holds the goods.
            BigDecimal unitPrice = pricingService.applyBest(listPrice(variant, product), discountsFor(product));

            if (variantRepository.reserveStock(variantId, quantity) == 0) {
                // Re-read only to make the message useful - the decision was already made
                // atomically by the statement above.
                int available = variantRepository.findById(variantId)
                        .map(ProductVariant::getStockQuantity)
                        .orElse(0);
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
        String code = request.couponCode();
        if (code != null && !code.isBlank()) {
            discountAmount = claimCoupon(code.trim(), subtotal, request.priorCouponUses());
            appliedCoupon = code.trim();
        }

        BigDecimal total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Reserved {} line(s): subtotal {}, discount {}, total {}",
                reserved.size(), subtotal, discountAmount, total);
        return new StockReservationResponse(reserved, subtotal, appliedCoupon, discountAmount, total);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public void release(StockReservationRequest request) {
        mergeLines(request.items()).forEach((variantId, quantity) -> {
            if (variantRepository.releaseStock(variantId, quantity) == 0) {
                // The variant vanished between reserve and release. There is nothing to put
                // back, and failing here would strand the caller mid-compensation.
                log.warn("Release found no variant {} - stock not restored", variantId);
            }
        });
        String code = request.couponCode();
        if (code != null && !code.isBlank()) {
            couponRepository.returnUse(code.trim());
        }
        log.info("Released reservation of {} line(s)", request.items().size());
    }

    /**
     * Validates the coupon and claims one use atomically.
     *
     * <p>maxUses was previously only checked during validation and then incremented
     * unconditionally at redemption, in a separate transaction - so two checkouts racing
     * through validation both passed and both got the discount, single-use welcome codes
     * included. The conditional UPDATE is what makes claiming a use safe; validation still
     * covers the rest (active, in-window, minimum spend).
     */
    private BigDecimal claimCoupon(String code, BigDecimal subtotal, Integer priorUses) {
        CouponValidationResponse validation =
                couponService.validate(new CouponValidateRequest(code, subtotal));
        if (!validation.valid()) {
            throw new ConflictException("Coupon not applicable: " + validation.message());
        }
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ConflictException("Coupon not applicable: Coupon not found"));
        Integer perUserLimit = coupon.getPerUserLimit();
        if (perUserLimit != null && priorUses != null && priorUses >= perUserLimit) {
            throw new ConflictException("Coupon not applicable: you have already used this code "
                    + priorUses + " time(s) and the limit is " + perUserLimit);
        }
        if (couponRepository.claimUse(code) == 0) {
            throw new ConflictException("Coupon not applicable: usage limit reached");
        }
        BigDecimal amount = validation.discountAmount();
        return amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Sums quantities per variant. Ascending variant id gives every concurrent reservation the
     * same row order, so two multi-line orders over the same variants cannot deadlock against
     * each other.
     */
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
}
