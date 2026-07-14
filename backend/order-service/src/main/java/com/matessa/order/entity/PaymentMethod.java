package com.matessa.order.entity;

public enum PaymentMethod {
    CARD,
    PAYPAL,
    COD,
    ONLINE   // gateway-hosted payment (Razorpay / PayPal); the actual charge is mocked in v1
}
