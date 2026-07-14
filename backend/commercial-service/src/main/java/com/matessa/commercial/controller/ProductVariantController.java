package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.ProductVariantRequest;
import com.matessa.commercial.dto.response.ProductVariantResponse;
import com.matessa.commercial.dto.response.VariantPriceStockResponse;
import com.matessa.commercial.service.ProductVariantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Variants live under their product for listing/creation; the single-variant routes
 * ({@code /api/variants/{id}}) are kept short for the order-service price/stock lookup.
 */
@RestController
public class ProductVariantController {

    private final ProductVariantService variantService;

    public ProductVariantController(ProductVariantService variantService) {
        this.variantService = variantService;
    }

    @GetMapping("/api/products/{productId}/variants")
    public List<ProductVariantResponse> list(@PathVariable Long productId) {
        return variantService.listForProduct(productId);
    }

    @PostMapping("/api/products/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductVariantResponse create(@PathVariable Long productId,
                                         @Valid @RequestBody ProductVariantRequest request) {
        return variantService.create(productId, request);
    }

    /** Order-facing: live price + stock for a variant. */
    @GetMapping("/api/variants/{id}")
    public VariantPriceStockResponse priceStock(@PathVariable Long id) {
        return variantService.getPriceStock(id);
    }

    @PutMapping("/api/variants/{id}")
    public ProductVariantResponse update(@PathVariable Long id, @Valid @RequestBody ProductVariantRequest request) {
        return variantService.update(id, request);
    }

    @DeleteMapping("/api/variants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        variantService.delete(id);
    }
}
