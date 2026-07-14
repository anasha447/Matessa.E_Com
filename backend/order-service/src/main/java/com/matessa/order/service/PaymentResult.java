package com.matessa.order.service;

import com.matessa.order.entity.PaymentStatus;

/** Outcome of a (mock) payment attempt. */
public record PaymentResult(
        PaymentStatus status,
        String transactionId
) {
}
