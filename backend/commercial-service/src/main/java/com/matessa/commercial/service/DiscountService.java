package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.DiscountRequest;
import com.matessa.commercial.dto.response.DiscountResponse;

import java.util.List;

public interface DiscountService {

    DiscountResponse create(DiscountRequest request, Long createdBy);

    DiscountResponse update(Long id, DiscountRequest request);

    void delete(Long id);

    DiscountResponse get(Long id);

    /** Active discounts that apply to a product (its own + cascading ancestor-category ones). */
    List<DiscountResponse> listApplicableForProduct(Long productId);
}
