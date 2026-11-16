package com.matessa.order.service.impl;

import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.service.PaymentResult;
import com.matessa.order.service.PaymentService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stand-in payment processor for v1 — no real gateway. CARD/PAYPAL settle immediately
 * with a generated transaction id; COD stays PENDING (collected on delivery). A real
 * gateway integration (Stripe/PayPal) would replace this without touching the order flow.
 */
@Service
@Profile("!prod")   // never in production: it reports card payments as paid without charging anything
public class MockPaymentService implements PaymentService {

    @Override
    public PaymentResult charge(PaymentMethod method, BigDecimal amount) {
        if (method == PaymentMethod.COD) {
            return new PaymentResult(PaymentStatus.PENDING, null);
        }
        String transactionId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return new PaymentResult(PaymentStatus.COMPLETED, transactionId);
    }

    @Override
    public java.util.List<PaymentMethod> supportedMethods() {
        return java.util.List.of(PaymentMethod.COD, PaymentMethod.ONLINE);
    }
}
