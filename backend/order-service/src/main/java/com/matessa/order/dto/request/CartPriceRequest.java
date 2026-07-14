package com.matessa.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Price a set of lines without persisting a cart — used by the guest (client-side) cart to
 * render live, discount-aware prices from the same source of truth as the server cart.
 */
public record CartPriceRequest(
        @NotNull List<@Valid CartItemRequest> items
) {
}
