package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.WishlistItemRequest;
import com.matessa.commercial.dto.response.WishlistItemResponse;
import com.matessa.commercial.dto.response.WishlistResponse;
import com.matessa.commercial.entity.Wishlist;
import com.matessa.commercial.entity.WishlistItem;
import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.mapper.WishlistMapper;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.repository.WishlistItemRepository;
import com.matessa.commercial.repository.WishlistRepository;
import com.matessa.commercial.service.WishlistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final WishlistMapper wishlistMapper;

    public WishlistServiceImpl(WishlistRepository wishlistRepository,
                               WishlistItemRepository itemRepository,
                               ProductVariantRepository variantRepository,
                               WishlistMapper wishlistMapper) {
        this.wishlistRepository = wishlistRepository;
        this.itemRepository = itemRepository;
        this.variantRepository = variantRepository;
        this.wishlistMapper = wishlistMapper;
    }

    @Override
    @Transactional
    public WishlistResponse getForUser(Long userId) {
        Wishlist wishlist = getOrCreate(userId);
        List<WishlistItemResponse> items = itemRepository
                .findByWishlistIdOrderByAddedAtDescIdDesc(wishlist.getId())
                .stream().map(wishlistMapper::toResponse).toList();
        return new WishlistResponse(userId, items);
    }

    @Override
    @Transactional
    public WishlistItemResponse addItem(Long userId, WishlistItemRequest request) {
        if (!variantRepository.existsById(request.productVariantId())) {
            throw new BadRequestException("Variant not found: " + request.productVariantId());
        }
        Wishlist wishlist = getOrCreate(userId);
        if (itemRepository.existsByWishlistIdAndProductVariantId(wishlist.getId(), request.productVariantId())) {
            throw new ConflictException("Variant already in wishlist: " + request.productVariantId());
        }
        WishlistItem item = new WishlistItem();
        item.setWishlistId(wishlist.getId());
        item.setProductVariantId(request.productVariantId());
        return wishlistMapper.toResponse(itemRepository.save(item));
    }

    @Override
    @Transactional
    public void removeItem(Long userId, Long itemId) {
        WishlistItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> notFound(itemId));
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                .orElseThrow(() -> notFound(itemId));
        if (!item.getWishlistId().equals(wishlist.getId())) {
            throw notFound(itemId);   // not this user's item — mask as not-found
        }
        itemRepository.delete(item);
    }

    private Wishlist getOrCreate(Long userId) {
        return wishlistRepository.findByUserId(userId).orElseGet(() -> {
            Wishlist wishlist = new Wishlist();
            wishlist.setUserId(userId);
            return wishlistRepository.save(wishlist);
        });
    }

    private ResourceNotFoundException notFound(Long itemId) {
        return new ResourceNotFoundException("Wishlist item not found: " + itemId);
    }
}
