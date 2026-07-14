package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.WishlistItemRequest;
import com.matessa.commercial.dto.response.WishlistItemResponse;
import com.matessa.commercial.dto.response.WishlistResponse;

public interface WishlistService {

    WishlistResponse getForUser(Long userId);

    WishlistItemResponse addItem(Long userId, WishlistItemRequest request);

    void removeItem(Long userId, Long itemId);
}
