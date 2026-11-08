package com.matessa.commercial.repository;

import com.matessa.commercial.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);

    /**
     * Takes {@code quantity} off the shelf, but only if it is actually there.
     *
     * <p>The guard lives in the WHERE clause, so the check and the decrement are one
     * statement and the database evaluates them atomically. Two concurrent callers competing
     * for the last unit cannot both succeed: one UPDATE matches a row, the other matches
     * none. Returns 1 when the stock was taken and 0 when it was not — a 0 means
     * insufficient stock, an inactive variant, or no such variant.
     *
     * <p>This is a stronger guarantee than reading the row and writing it back under
     * {@code @Version}: that pattern only detects a lost update after the fact, and the read
     * it validated against may already have been used to make a decision (here, to take a
     * payment).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductVariant v set v.stockQuantity = v.stockQuantity - :quantity "
            + "where v.id = :id and v.active = true and v.stockQuantity >= :quantity")
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** Puts stock back — compensating a failed payment or a cancelled order. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductVariant v set v.stockQuantity = v.stockQuantity + :quantity where v.id = :id")
    int releaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);
}
