package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.ReviewRequest;
import com.matessa.commercial.dto.response.ReviewResponse;
import com.matessa.commercial.dto.response.ReviewSummaryResponse;
import com.matessa.commercial.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Product reviews. Reading is public; writing requires a logged-in user who may only
 * touch their own review (one per product). Staff (MANAGER/ADMIN) may delete any review
 * for moderation.
 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    public Page<ReviewResponse> list(@PathVariable Long productId, Pageable pageable) {
        return reviewService.listForProduct(productId, pageable);
    }

    @GetMapping("/products/{productId}/reviews/summary")
    public ReviewSummaryResponse summary(@PathVariable Long productId) {
        return reviewService.summary(productId);
    }

    @PostMapping("/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@PathVariable Long productId,
                                 @AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(productId, userId, request);
    }

    @PutMapping("/reviews/{reviewId}")
    public ReviewResponse update(@PathVariable Long reviewId,
                                 @AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(reviewId, userId, request);
    }

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long reviewId,
                       @AuthenticationPrincipal Long userId,
                       Authentication authentication) {
        boolean staff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_MANAGER") || a.equals("ROLE_ADMIN"));
        reviewService.delete(reviewId, userId, staff);
    }
}
