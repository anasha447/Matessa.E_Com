package com.matessa.order.service;

import com.matessa.order.entity.PaymentMethod;

import java.math.BigDecimal;

public interface PaymentService {

    /** Attempt payment for an order total. */
    PaymentResult charge(PaymentMethod method, BigDecimal amount);
}
