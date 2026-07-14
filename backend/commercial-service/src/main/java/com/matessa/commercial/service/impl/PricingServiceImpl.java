package com.matessa.commercial.service.impl;

import com.matessa.commercial.entity.Category;
import com.matessa.commercial.entity.Discount;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.repository.DiscountRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.service.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PricingServiceImpl implements PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_DEPTH = 50;   // cycle guard for the category chain

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final DiscountRepository discountRepository;

    public PricingServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              DiscountRepository discountRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.discountRepository = discountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Discount> activeDiscountsForProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return activeDiscountsFor(product.getId(), product.getCategoryId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Discount> activeDiscountsFor(Long productId, Long categoryId) {
        return discountRepository.findActive(productId, categoryChain(categoryId), Instant.now());
    }

    @Override
    public BigDecimal applyBest(BigDecimal listPrice, List<Discount> discounts) {
        BigDecimal best = listPrice;
        for (Discount discount : discounts) {
            BigDecimal candidate = priceAfter(listPrice, discount);
            if (candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal priceAfter(BigDecimal listPrice, Discount discount) {
        BigDecimal reduced = switch (discount.getType()) {
            case PERCENTAGE -> listPrice.subtract(
                    listPrice.multiply(discount.getValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            case FIXED_AMOUNT -> listPrice.subtract(discount.getValue());
        };
        return reduced.max(BigDecimal.ZERO);
    }

    /** The product's category plus all of its ancestors, so a parent-category discount cascades. */
    private List<Long> categoryChain(Long categoryId) {
        List<Long> chain = new ArrayList<>();
        Long current = categoryId;
        int depth = 0;
        while (current != null && depth++ < MAX_DEPTH) {
            chain.add(current);
            current = categoryRepository.findById(current).map(Category::getParentId).orElse(null);
        }
        return chain;
    }
}
