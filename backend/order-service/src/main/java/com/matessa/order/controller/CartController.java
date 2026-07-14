package com.matessa.order.controller;

import com.matessa.order.dto.request.CartItemRequest;
import com.matessa.order.dto.request.CartPriceRequest;
import com.matessa.order.dto.request.UpdateCartItemRequest;
import com.matessa.order.dto.response.CartResponse;
import com.matessa.order.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own cart (userId from the JWT). Lines are shown at live, discount-aware
 * prices fetched from commercial-service.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Long userId) {
        return cartService.getCart(userId);
    }

    /** Public: live-price a guest cart (client-side items) without persisting anything. */
    @PostMapping("/price")
    public CartResponse price(@Valid @RequestBody CartPriceRequest request) {
        return cartService.priceItems(request.items());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse add(@AuthenticationPrincipal Long userId, @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(userId, request);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long itemId,
                               @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(userId, itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Long userId, @PathVariable Long itemId) {
        cartService.removeItem(userId, itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Long userId) {
        cartService.clear(userId);
    }
}
