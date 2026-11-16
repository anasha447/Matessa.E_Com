package com.matessa.commercial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** The unit actually stocked and purchased. {@code @Version} prevents overselling. */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, unique = true)
    private String sku;

    private String size;

    private String color;

    @Column(name = "price_override")
    private BigDecimal priceOverride;   // null = use Product.basePrice

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "restock_level")
    private Integer restockLevel;

    @Column(nullable = false)
    private boolean active = true;   // soft delete

    /**
     * The hidden variant of a product that has no variations: it holds the product's own stock.
     * Inactive while the product has real variations (sizes/colours).
     */
    @Column(name = "is_default", nullable = false)
    private boolean defaultVariant = false;

    @Version
    private Long version;   // optimistic lock (anti-oversell)
}
