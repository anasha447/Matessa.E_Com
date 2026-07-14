package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.WishlistItemRequest;
import com.matessa.commercial.dto.response.WishlistItemResponse;
import com.matessa.commercial.dto.response.WishlistResponse;
import com.matessa.commercial.service.WishlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-customer wishlist. Every endpoint operates on the caller's own wishlist
 * (userId from the verified JWT); it is never shared or publicly readable.
 */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public WishlistResponse get(@AuthenticationPrincipal Long userId) {
        return wishlistService.getForUser(userId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public WishlistItemResponse add(@AuthenticationPrincipal Long userId,
                                    @Valid @RequestBody WishlistItemRequest request) {
        return wishlistService.addItem(userId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Long userId, @PathVariable Long itemId) {
        wishlistService.removeItem(userId, itemId);
    }
}
