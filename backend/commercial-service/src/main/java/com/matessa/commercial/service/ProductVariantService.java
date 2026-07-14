package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.ProductVariantRequest;
import com.matessa.commercial.dto.response.ProductVariantResponse;
import com.matessa.commercial.dto.response.VariantPriceStockResponse;

import java.util.List;

public interface ProductVariantService {

    List<ProductVariantResponse> listForProduct(Long productId);

    ProductVariantResponse create(Long productId, ProductVariantRequest request);

    ProductVariantResponse update(Long variantId, ProductVariantRequest request);

    void delete(Long variantId);

    /** Live price + stock for order-service (Feign at checkout). */
    VariantPriceStockResponse getPriceStock(Long variantId);
}
