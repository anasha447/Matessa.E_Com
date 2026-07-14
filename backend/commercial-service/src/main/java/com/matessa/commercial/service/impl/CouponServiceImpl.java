package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.CouponRequest;
import com.matessa.commercial.dto.request.CouponValidateRequest;
import com.matessa.commercial.dto.response.CouponResponse;
import com.matessa.commercial.dto.response.CouponValidationResponse;
import com.matessa.commercial.entity.Coupon;
import com.matessa.commercial.entity.DiscountType;
import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.mapper.CouponMapper;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.service.CouponService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CouponServiceImpl implements CouponService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;

    public CouponServiceImpl(CouponRepository couponRepository, CouponMapper couponMapper) {
        this.couponRepository = couponRepository;
        this.couponMapper = couponMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CouponResponse> list(Pageable pageable) {
        return couponRepository.findAll(pageable).map(couponMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse get(Long id) {
        return couponMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public CouponResponse create(CouponRequest request) {
        if (couponRepository.existsByCode(request.code())) {
            throw new ConflictException("Coupon code already exists: " + request.code());
        }
        validateWindow(request.validFrom(), request.validUntil());
        Coupon coupon = new Coupon();
        coupon.setUsedCount(0);
        apply(coupon, request);
        return couponMapper.toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = find(id);
        if (couponRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("Coupon code already exists: " + request.code());
        }
        validateWindow(request.validFrom(), request.validUntil());
        apply(coupon, request);
        return couponMapper.toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Coupon coupon = find(id);
        coupon.setActive(false);   // soft delete — orders may reference the code historically
        couponRepository.save(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validate(CouponValidateRequest request) {
        Coupon coupon = couponRepository.findByCode(request.code()).orElse(null);
        if (coupon == null) {
            return invalid(request.code(), "Coupon not found");
        }
        if (!coupon.isActive()) {
            return invalid(request.code(), "Coupon is not active");
        }
        Instant now = Instant.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil())) {
            return invalid(request.code(), "Coupon is expired or not yet valid");
        }
        if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) {
            return invalid(request.code(), "Coupon usage limit reached");
        }
        if (coupon.getMinSpend() != null && request.subtotal().compareTo(coupon.getMinSpend()) < 0) {
            return invalid(request.code(), "Subtotal is below the minimum spend of " + coupon.getMinSpend());
        }
        BigDecimal amount = discountAmount(coupon, request.subtotal());
        return new CouponValidationResponse(true, coupon.getCode(), coupon.getDiscountType(),
                coupon.getDiscountValue(), amount, "Valid");
    }

    @Override
    @Transactional
    public String issueWelcomeCoupon() {
        Coupon coupon = new Coupon();
        coupon.setCode(uniqueWelcomeCode());
        coupon.setDiscountType(DiscountType.PERCENTAGE);
        coupon.setDiscountValue(BigDecimal.valueOf(WELCOME_DISCOUNT_PERCENT));
        coupon.setMaxUses(1);            // single use
        coupon.setUsedCount(0);
        coupon.setPerUserLimit(1);
        coupon.setValidFrom(Instant.now());
        coupon.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));
        coupon.setActive(true);
        return couponRepository.save(coupon).getCode();
    }

    private String uniqueWelcomeCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "WELCOME15-" + randomCode(6);
            if (!couponRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique welcome coupon code");
    }

    private static String randomCode(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // no ambiguous 0/O/1/I
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private BigDecimal discountAmount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal amount = switch (coupon.getDiscountType()) {
            case PERCENTAGE -> subtotal.multiply(coupon.getDiscountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> coupon.getDiscountValue();
        };
        return amount.min(subtotal).setScale(2, RoundingMode.HALF_UP);   // never more than the subtotal
    }

    private CouponValidationResponse invalid(String code, String message) {
        return new CouponValidationResponse(false, code, null, null,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), message);
    }

    private Coupon find(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));
    }

    private void validateWindow(Instant from, Instant until) {
        if (!until.isAfter(from)) {
            throw new BadRequestException("validUntil must be after validFrom");
        }
    }

    private void apply(Coupon coupon, CouponRequest request) {
        coupon.setCode(request.code());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMinSpend(request.minSpend());
        coupon.setMaxUses(request.maxUses());
        coupon.setPerUserLimit(request.perUserLimit());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setActive(request.active() == null || request.active());
    }
}
