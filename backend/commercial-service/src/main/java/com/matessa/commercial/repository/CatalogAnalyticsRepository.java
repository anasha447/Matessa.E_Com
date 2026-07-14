package com.matessa.commercial.repository;

import com.matessa.commercial.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read-only aggregate queries for the catalog/inventory dashboard. */
public interface CatalogAnalyticsRepository extends Repository<Product, Long> {

    @Query("select count(p) from Product p where p.active = true")
    long activeProductCount();

    @Query("select count(v) from ProductVariant v where v.active = true")
    long activeVariantCount();

    @Query("select count(c) from Category c where c.active = true")
    long activeCategoryCount();

    @Query("select coalesce(sum(v.stockQuantity * coalesce(v.priceOverride, p.basePrice)), 0) "
            + "from ProductVariant v, Product p where p.id = v.productId and v.active = true and p.active = true")
    BigDecimal totalInventoryValue();

    @Query("select count(v) from ProductVariant v "
            + "where v.active = true and v.restockLevel is not null and v.stockQuantity <= v.restockLevel")
    long lowStockCount();

    @Query("select count(v) from ProductVariant v where v.active = true and v.stockQuantity = 0")
    long outOfStockCount();

    @Query("select v.id, v.sku, v.stockQuantity, v.restockLevel from ProductVariant v "
            + "where v.active = true and v.restockLevel is not null and v.stockQuantity <= v.restockLevel "
            + "order by v.stockQuantity asc")
    List<Object[]> lowStockItems(Pageable pageable);

    @Query("select count(r) from Review r")
    long reviewCount();

    @Query("select p.id, p.name, avg(r.rating), count(r) from Review r, Product p "
            + "where p.id = r.productId group by p.id, p.name order by avg(r.rating) desc")
    List<Object[]> topRatedProducts(Pageable pageable);

    @Query("select v.id, v.sku, count(w) from WishlistItem w, ProductVariant v "
            + "where v.id = w.productVariantId group by v.id, v.sku order by count(w) desc")
    List<Object[]> mostWishlisted(Pageable pageable);

    @Query("select count(c) from Coupon c where c.active = true")
    long activeCouponCount();

    @Query("select count(d) from Discount d where d.active = true and d.validFrom <= :now and d.validTo >= :now")
    long activeDiscountCount(@Param("now") Instant now);
}
