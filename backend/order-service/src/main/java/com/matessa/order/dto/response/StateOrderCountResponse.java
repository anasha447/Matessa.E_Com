package com.matessa.order.dto.response;

/** Orders shipped to one state (admin India-map analytics). "Other" groups orders with no state. */
public record StateOrderCountResponse(
        String state,
        long orderCount
) {
}
