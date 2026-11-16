package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.ProductVariantRequest;
import com.matessa.commercial.dto.response.ProductVariantResponse;
import com.matessa.commercial.dto.response.VariantPriceStockResponse;
import com.matessa.commercial.entity.Discount;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.service.PricingService;
import com.matessa.commercial.service.ProductVariantService;
import com.matessa.commercial.service.SkuGenerator;
import com.matessa.commercial.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final PricingService pricingService;
    private final SkuGenerator skuGenerator;

    public ProductVariantServiceImpl(ProductVariantRepository variantRepository,
                                     ProductRepository productRepository,
                                     PricingService pricingService,
                                     SkuGenerator skuGenerator) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.pricingService = pricingService;
        this.skuGenerator = skuGenerator;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.VARIANT_LIST, key = "#productId")
    public List<ProductVariantResponse> listForProduct(Long productId) {
        Product product = findProduct(productId);
        List<Discount> discounts = discountsFor(product);
        return variantRepository.findByProductIdOrderByIdAsc(productId).stream()
                .filter(ProductVariant::isActive)
                .map(variant -> toResponse(variant, product, discounts))
                .toList();
    }

    // Variants carry display price/stock and belong to the product read-model → evict both caches.
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, allEntries = true)
    })
    public ProductVariantResponse create(Long productId, ProductVariantRequest request) {
        Product product = findProduct(productId);
        ProductVariant variant = new ProductVariant();
        variant.setProductId(productId);
        // SKU is generated, never client-supplied — derived from the product + size/colour
        // and made unique against existing SKUs.
        variant.setSku(skuGenerator.generate(product.getName(), request.size(), request.color(),
                variantRepository::existsBySku));
        apply(variant, request);
        // The product now has real variations: its hidden default variant (product-level stock)
        // stops being sold. Each variation carries its own stock from here on.
        variantRepository.findByProductIdAndDefaultVariantTrue(productId).ifPresent(def -> {
            if (def.isActive()) {
                def.setActive(false);
                variantRepository.save(def);
            }
        });
        return toResponse(variantRepository.save(variant), product, discountsFor(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, allEntries = true)
    })
    public ProductVariantResponse update(Long variantId, ProductVariantRequest request) {
        ProductVariant variant = findVariant(variantId);
        // SKU is immutable once generated — edits to size/colour don't churn the identifier.
        apply(variant, request);
        if (variant.isDefaultVariant()) {
            // The default variant is the product itself: only stock, price and restock level apply.
            variant.setSize(null);
            variant.setColor(null);
        }
        Product product = findProduct(variant.getProductId());
        return toResponse(variantRepository.save(variant), product, discountsFor(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, allEntries = true)
    })
    public void delete(Long variantId) {
        ProductVariant variant = findVariant(variantId);
        if (variant.isDefaultVariant()) {
            throw new com.matessa.commercial.exception.BadRequestException(
                    "This is the product's own stock; set it to 0 instead, or delete the product.");
        }
        variant.setActive(false); // soft delete
        variantRepository.save(variant);
        // Last real variation gone: the product is sold as itself again, from its default
        // variant, starting out of stock until an admin sets the product's stock.
        if (variantRepository.countByProductIdAndDefaultVariantFalseAndActiveTrue(variant.getProductId()) == 0) {
            ProductVariant def = variantRepository.findByProductIdAndDefaultVariantTrue(variant.getProductId())
                    .orElseGet(() -> newDefault(findProduct(variant.getProductId())));
            def.setActive(true);
            def.setStockQuantity(0);
            variantRepository.save(def);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public VariantPriceStockResponse getPriceStock(Long variantId) {
        ProductVariant variant = findVariant(variantId);
        Product product = findProduct(variant.getProductId());
        boolean available = product.isActive() && variant.isActive();
        BigDecimal effective = pricingService.applyBest(listPrice(variant, product), discountsFor(product));
        return new VariantPriceStockResponse(variant.getId(), product.getId(), variant.getSku(),
                product.getName(), effective, variant.getStockQuantity(), available);
    }

    /** A fresh hidden default variant for a product (not yet saved). */
    private ProductVariant newDefault(Product product) {
        ProductVariant def = new ProductVariant();
        def.setProductId(product.getId());
        def.setSku(skuGenerator.generate(product.getName(), null, null, variantRepository::existsBySku));
        def.setStockQuantity(0);
        def.setActive(true);
        def.setDefaultVariant(true);
        return def;
    }

    private List<Discount> discountsFor(Product product) {
        return pricingService.activeDiscountsFor(product.getId(), product.getCategoryId());
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private ProductVariant findVariant(Long id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + id));
    }

    private void apply(ProductVariant variant, ProductVariantRequest request) {
        variant.setSize(request.size());
        variant.setColor(request.color());
        variant.setPriceOverride(request.priceOverride());
        variant.setStockQuantity(request.stockQuantity());
        variant.setRestockLevel(request.restockLevel());
        variant.setActive(true);
    }

    private BigDecimal listPrice(ProductVariant variant, Product product) {
        return variant.getPriceOverride() != null ? variant.getPriceOverride() : product.getBasePrice();
    }

    private ProductVariantResponse toResponse(ProductVariant variant, Product product, List<Discount> discounts) {
        BigDecimal listPrice = listPrice(variant, product);
        BigDecimal effective = pricingService.applyBest(listPrice, discounts);
        return new ProductVariantResponse(variant.getId(), variant.getProductId(), variant.getSku(),
                variant.getSize(), variant.getColor(), variant.getPriceOverride(),
                listPrice, effective, variant.getStockQuantity(), variant.getRestockLevel(),
                variant.isActive(), variant.isDefaultVariant());
    }
}
