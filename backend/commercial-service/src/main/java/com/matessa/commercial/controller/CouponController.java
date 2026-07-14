package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.CouponRequest;
import com.matessa.commercial.dto.request.CouponValidateRequest;
import com.matessa.commercial.dto.response.CouponResponse;
import com.matessa.commercial.dto.response.CouponValidationResponse;
import com.matessa.commercial.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coupons are staff-managed (MANAGER/ADMIN). The only customer-facing endpoint is
 * {@code POST /validate}, a stateless checkout preview that never redeems the code.
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public Page<CouponResponse> list(Pageable pageable) {
        return couponService.list(pageable);
    }

    @GetMapping("/{id}")
    public CouponResponse get(@PathVariable Long id) {
        return couponService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CouponRequest request) {
        return couponService.create(request);
    }

    @PutMapping("/{id}")
    public CouponResponse update(@PathVariable Long id, @Valid @RequestBody CouponRequest request) {
        return couponService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        couponService.delete(id);
    }

    @PostMapping("/validate")
    public CouponValidationResponse validate(@Valid @RequestBody CouponValidateRequest request) {
        return couponService.validate(request);
    }
}
