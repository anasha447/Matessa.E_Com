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
        // Idempotent: an already-subscribed email does not mint a second coupon.
        //
        // It also must NOT get the stored coupon code back. This endpoint is public and
        // unauthenticated, and the caller does not have to own the address they submit —
        // echoing the stored code turned it into a coupon-theft oracle: submit any address
        // you know and receive that person's welcome code.
        //
        // Without an email channel the code can only be shown inline on first signup, so a
        // repeat signup returns no code at all. Residual: the differing message still reveals
        // whether an address is subscribed. Closing that needs verified email delivery, where
        // the code goes to the inbox and this response becomes identical either way.
        Subscriber existing = subscriberRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            return new SubscribeResponse(existing.getEmail(), null,
                    CouponService.WELCOME_DISCOUNT_PERCENT,
                    "You're already on the list — your welcome code was issued when you first signed up.");
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
