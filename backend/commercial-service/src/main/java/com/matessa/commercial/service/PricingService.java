package com.matessa.commercial.service;

import com.matessa.commercial.entity.Discount;

import java.math.BigDecimal;
import java.util.List;

/** Resolves automatic discounts and folds the best one into a variant's effective price. */
public interface PricingService {

    /** Active, in-window discounts targeting the product directly or any ancestor category. */
    List<Discount> activeDiscountsFor(Long productId, Long categoryId);

    /** Same as above, but loads the product first to find its category. */
    List<Discount> activeDiscountsForProduct(Long productId);

    /** Apply the single best (largest reduction) discount to a list price; floored at zero, scale 2. */
    BigDecimal applyBest(BigDecimal listPrice, List<Discount> discounts);
}
