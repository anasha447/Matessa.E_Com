package com.matessa.commercial.repository;

import com.matessa.commercial.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    /**
     * Active, in-window discounts that target this product directly OR any category in the
     * product's category-ancestor chain (so a parent-category discount cascades down).
     */
    @Query("""
            select d from Discount d
            where d.active = true and d.validFrom <= :now and d.validTo >= :now
              and (d.productId = :productId or d.categoryId in :categoryIds)
            """)
    List<Discount> findActive(@Param("productId") Long productId,
                              @Param("categoryIds") Collection<Long> categoryIds,
                              @Param("now") Instant now);
}
