package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.ReviewRequest;
import com.matessa.commercial.dto.response.ReviewResponse;
import com.matessa.commercial.dto.response.ReviewSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    Page<ReviewResponse> listForProduct(Long productId, Pageable pageable);

    ReviewSummaryResponse summary(Long productId);

    ReviewResponse create(Long productId, Long userId, ReviewRequest request);

    ReviewResponse update(Long reviewId, Long userId, ReviewRequest request);

    void delete(Long reviewId, Long userId, boolean staff);
}
