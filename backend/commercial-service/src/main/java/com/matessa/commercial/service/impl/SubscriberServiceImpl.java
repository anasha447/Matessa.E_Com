package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.SubscribeRequest;
import com.matessa.commercial.dto.response.SubscribeResponse;
import com.matessa.commercial.dto.response.SubscriberResponse;
import com.matessa.commercial.entity.Subscriber;
import com.matessa.commercial.mapper.SubscriberMapper;
import com.matessa.commercial.repository.SubscriberRepository;
import com.matessa.commercial.service.CouponService;
import com.matessa.commercial.service.SubscriberService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SubscriberServiceImpl implements SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final SubscriberMapper subscriberMapper;
    private final CouponService couponService;

    public SubscriberServiceImpl(SubscriberRepository subscriberRepository,
                                 SubscriberMapper subscriberMapper,
                                 CouponService couponService) {
        this.subscriberRepository = subscriberRepository;
        this.subscriberMapper = subscriberMapper;
        this.couponService = couponService;
    }

    @Override
    @Transactional
    public SubscribeResponse join(SubscribeRequest request) {
        String email = request.email().trim().toLowerCase();
        // Idempotent: an already-subscribed email gets its existing coupon back (no dup coupon).
        Subscriber existing = subscriberRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            return new SubscribeResponse(existing.getEmail(), existing.getCouponCode(),
                    CouponService.WELCOME_DISCOUNT_PERCENT,
                    "You're already on the list — here's your code again.");
        }
        // Mint the welcome coupon in the same transaction as the subscriber row, so a rollback
        // never leaves an orphan coupon.
        String couponCode = couponService.issueWelcomeCoupon();
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        subscriber.setCouponCode(couponCode);
        subscriber.setSource("popup");
        subscriber.setCreatedAt(Instant.now());
        subscriberRepository.save(subscriber);
        return new SubscribeResponse(email, couponCode, CouponService.WELCOME_DISCOUNT_PERCENT,
                "Welcome aboard! Use this code for " + CouponService.WELCOME_DISCOUNT_PERCENT
                        + "% off your first order.");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriberResponse> list(Pageable pageable) {
        return subscriberRepository.findAllByOrderByCreatedAtDesc(pageable).map(subscriberMapper::toResponse);
    }
}
