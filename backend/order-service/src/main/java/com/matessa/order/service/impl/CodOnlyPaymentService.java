package com.matessa.order.service.impl;

import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.exception.BadRequestException;
import com.matessa.order.service.PaymentResult;
import com.matessa.order.service.PaymentService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Production payments until a real provider is connected: cash on delivery only.
 *
 * <p>The mock processor marked every card/online order PAID without taking any money; loaded in
 * production, any guest could place a "paid" order for free and staff would ship it. Under the
 * {@code prod} profile the mock is not loaded at all and online methods are refused outright.
 * Connecting Razorpay (or similar) means adding a provider implementation that only reports
 * COMPLETED from a signature-verified webhook.
 */
@Service
@Profile("prod")
public class CodOnlyPaymentService implements PaymentService {

    @Override
    public PaymentResult charge(PaymentMethod method, BigDecimal amount) {
        if (method != PaymentMethod.COD) {
            throw new BadRequestException("Online payment is not available yet — please choose cash on delivery.");
        }
        return new PaymentResult(PaymentStatus.PENDING, null); // collected on delivery
    }

    @Override
    public List<PaymentMethod> supportedMethods() {
        return List.of(PaymentMethod.COD);
    }
}
