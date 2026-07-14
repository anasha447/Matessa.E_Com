package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.ReviewRequest;
import com.matessa.commercial.dto.response.ReviewResponse;
import com.matessa.commercial.dto.response.ReviewSummaryResponse;
import com.matessa.commercial.entity.Review;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.mapper.ReviewMapper;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ReviewRepository;
import com.matessa.commercial.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewMapper reviewMapper;

    public ReviewServiceImpl(ReviewRepository reviewRepository,
                             ProductRepository productRepository,
                             ReviewMapper reviewMapper) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.reviewMapper = reviewMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> listForProduct(Long productId, Pageable pageable) {
        ensureProductExists(productId);
        return reviewRepository.findByProductId(productId, pageable).map(reviewMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long productId) {
        ensureProductExists(productId);
        double average = reviewRepository.averageRating(productId);
        double rounded = Math.round(average * 100.0) / 100.0;
        return new ReviewSummaryResponse(productId, rounded, reviewRepository.countByProductId(productId));
    }

    @Override
    @Transactional
    public ReviewResponse create(Long productId, Long userId, ReviewRequest request) {
        ensureProductExists(productId);
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException("You have already reviewed this product");
        }
        Review review = new Review();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setRating(request.rating());
        review.setText(request.text());
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponse update(Long reviewId, Long userId, ReviewRequest request) {
        Review review = ownedReview(reviewId, userId);
        review.setRating(request.rating());
        review.setText(request.text());
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void delete(Long reviewId, Long userId, boolean staff) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> notFound(reviewId));
        if (!staff && !review.getUserId().equals(userId)) {
            throw notFound(reviewId);   // don't reveal other users' reviews
        }
        reviewRepository.delete(review);
    }

    private Review ownedReview(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> notFound(reviewId));
        if (!review.getUserId().equals(userId)) {
            throw notFound(reviewId);   // editing is owner-only; mask others' reviews as not-found
        }
        return review;
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    private ResourceNotFoundException notFound(Long reviewId) {
        return new ResourceNotFoundException("Review not found: " + reviewId);
    }
}
