package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.CouponRequest;
import com.matessa.commercial.dto.request.CouponValidateRequest;
import com.matessa.commercial.dto.response.CouponResponse;
import com.matessa.commercial.dto.response.CouponValidationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CouponService {

    Page<CouponResponse> list(Pageable pageable);

    CouponResponse get(Long id);

    CouponResponse create(CouponRequest request);

    CouponResponse update(Long id, CouponRequest request);

    void delete(Long id);

    /** Stateless checkout preview — does NOT redeem (usedCount is incremented at order time). */
    CouponValidationResponse validate(CouponValidateRequest request);

    /** Percentage off granted by the newsletter welcome coupon (single source of truth). */
    int WELCOME_DISCOUNT_PERCENT = 15;

    /** Mint a fresh single-use welcome coupon for a newsletter signup and return its code. */
    String issueWelcomeCoupon();
}
