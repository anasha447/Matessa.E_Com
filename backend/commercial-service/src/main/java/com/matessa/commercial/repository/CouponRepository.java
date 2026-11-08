package com.matessa.commercial.repository;

import com.matessa.commercial.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    /**
     * Consumes one use, but only if one is left.
     *
     * <p>maxUses was previously checked when the coupon was *validated* and then incremented
     * unconditionally at redemption, with the two steps in different transactions — so two
     * checkouts racing through validation both passed and both got the discount, including on
     * a single-use welcome code. Putting the limit in the WHERE clause makes claiming a use
     * atomic: returns 1 when a use was claimed, 0 when the coupon is exhausted.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Coupon c set c.usedCount = c.usedCount + 1 "
            + "where c.code = :code and (c.maxUses is null or c.usedCount < c.maxUses)")
    int claimUse(@Param("code") String code);

    /** Gives a use back — compensating a failed payment or a cancelled order. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Coupon c set c.usedCount = c.usedCount - 1 where c.code = :code and c.usedCount > 0")
    int returnUse(@Param("code") String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
