package com.matessa.order.service.impl;

import com.matessa.order.client.CommercialClient;
import com.matessa.order.client.VariantPriceStock;
import com.matessa.order.dto.request.CartItemRequest;
import com.matessa.order.dto.response.CartLineResponse;
import com.matessa.order.dto.response.CartResponse;
import com.matessa.order.entity.Cart;
import com.matessa.order.entity.CartItem;
import com.matessa.order.exception.BadRequestException;
import com.matessa.order.exception.ResourceNotFoundException;
import com.matessa.order.repository.CartItemRepository;
import com.matessa.order.repository.CartRepository;
import com.matessa.order.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository itemRepository;
    private final CommercialClient commercialClient;

    public CartServiceImpl(CartRepository cartRepository,
                           CartItemRepository itemRepository,
                           CommercialClient commercialClient) {
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.commercialClient = commercialClient;
    }

    @Override
    @Transactional
    public CartResponse getCart(Long userId) {
        return buildResponse(getOrCreate(userId));
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemRequest request) {
        // validate the variant exists + is purchasable (propagates 503 if commercial is down)
        VariantPriceStock variant = commercialClient.getVariantPriceStock(request.productVariantId());
        if (!variant.available()) {
            throw new BadRequestException("Variant is not available: " + request.productVariantId());
        }
        Cart cart = getOrCreate(userId);
        CartItem item = itemRepository.findByCartIdAndProductVariantId(cart.getId(), request.productVariantId())
                .orElse(null);
        if (item == null) {
            item = new CartItem();
            item.setCartId(cart.getId());
            item.setProductVariantId(request.productVariantId());
            item.setQuantity(request.quantity());
        } else {
            item.setQuantity(item.getQuantity() + request.quantity());   // merge into the existing line
        }
        itemRepository.save(item);
        touch(cart);
        return buildResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, int quantity) {
        Cart cart = getOrCreate(userId);
        CartItem item = ownedItem(cart, itemId);
        item.setQuantity(quantity);
        itemRepository.save(item);
        touch(cart);
        return buildResponse(cart);
    }

    @Override
    @Transactional
    public void removeItem(Long userId, Long itemId) {
        Cart cart = getOrCreate(userId);
        itemRepository.delete(ownedItem(cart, itemId));
        touch(cart);
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        Cart cart = getOrCreate(userId);
        itemRepository.deleteByCartId(cart.getId());
        touch(cart);
    }

    private Cart getOrCreate(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUserId(userId);
            return cartRepository.save(cart);
        });
    }

    private CartItem ownedItem(Cart cart, Long itemId) {
        CartItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        if (!item.getCartId().equals(cart.getId())) {
            throw new ResourceNotFoundException("Cart item not found: " + itemId);   // not this user's item
        }
        return item;
    }

    private void touch(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }

    private CartResponse buildResponse(Cart cart) {
        List<CartLineResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : itemRepository.findByCartIdOrderByIdAsc(cart.getId())) {
            CartLineResponse line = enrich(item);
            lines.add(line);
            if (line.available() && line.lineTotal() != null) {
                subtotal = subtotal.add(line.lineTotal());
            }
        }
        return new CartResponse(cart.getId(), cart.getUserId(), lines, subtotal.setScale(2, RoundingMode.HALF_UP));
    }

    /** Look up the live price/stock; degrade the line to "unavailable" if the catalog is down. */
    private CartLineResponse enrich(CartItem item) {
        return enrichLine(item.getId(), item.getProductVariantId(), item.getQuantity());
    }

    /** Price a single (variant, quantity) with no persistence — shared by the server cart
     *  and the stateless guest-cart pricing endpoint. {@code lineId} is the cart-item id for
     *  a real cart, or the variant id for a guest line (which needs only a stable key). */
    private CartLineResponse enrichLine(Long lineId, Long variantId, int quantity) {
        try {
            VariantPriceStock variant = commercialClient.getVariantPriceStock(variantId);
            if (!variant.available()) {
                return new CartLineResponse(lineId, variantId, variant.productName(),
                        variant.sku(), variant.effectivePrice(), quantity, null, false, variant.stockQuantity());
            }
            BigDecimal lineTotal = variant.effectivePrice()
                    .multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            return new CartLineResponse(lineId, variantId, variant.productName(),
                    variant.sku(), variant.effectivePrice(), quantity, lineTotal, true, variant.stockQuantity());
        } catch (RuntimeException ex) {
            return new CartLineResponse(lineId, variantId, null, null, null, quantity, null, false, 0);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse priceItems(List<CartItemRequest> items) {
        List<CartLineResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItemRequest item : items) {
            // guest lines carry no cart-item id — use the variant id as the stable key
            CartLineResponse line = enrichLine(item.productVariantId(), item.productVariantId(), item.quantity());
            lines.add(line);
            if (line.available() && line.lineTotal() != null) {
                subtotal = subtotal.add(line.lineTotal());
            }
        }
        return new CartResponse(null, null, lines, subtotal.setScale(2, RoundingMode.HALF_UP));
    }
}
