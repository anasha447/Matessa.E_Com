package com.matessa.order.service;

import com.matessa.order.dto.request.CartItemRequest;
import com.matessa.order.dto.response.CartResponse;

import java.util.List;

public interface CartService {

    CartResponse getCart(Long userId);

    /** Live-price a set of lines without persisting anything (guest cart display). */
    CartResponse priceItems(List<CartItemRequest> items);

    CartResponse addItem(Long userId, CartItemRequest request);

    CartResponse updateItem(Long userId, Long itemId, int quantity);

    void removeItem(Long userId, Long itemId);

    void clear(Long userId);
}
