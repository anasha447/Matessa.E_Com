package com.matessa.commercial.repository;

import com.matessa.commercial.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, String> {

    /** Row-locked read: concurrent reserve/release/confirm of one reservation serialise here. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.id = :id")
    Optional<StockReservation> lockById(@Param("id") String id);

    /** Ids of reservations still held past their expiry (the expiry job's work list). */
    @Query("select r.id from StockReservation r where r.status = com.matessa.commercial.entity.StockReservation.Status.HELD "
            + "and r.expiresAt < :now order by r.expiresAt")
    List<String> findExpiredHeldIds(@Param("now") Instant now, org.springframework.data.domain.Pageable page);

    // ---- per-customer coupon redemptions (native: a plain counting table) ----

    @Query(value = "select count(*) from coupon_redemption where coupon_code = :code and customer_key = :customer",
            nativeQuery = true)
    long countRedemptions(@Param("code") String code, @Param("customer") String customer);

    @Modifying
    @Query(value = "insert into coupon_redemption (coupon_code, customer_key, reservation_id) values (:code, :customer, :rid)",
            nativeQuery = true)
    int addRedemption(@Param("code") String code, @Param("customer") String customer, @Param("rid") String reservationId);

    @Modifying
    @Query(value = "delete from coupon_redemption where reservation_id = :rid", nativeQuery = true)
    int removeRedemption(@Param("rid") String reservationId);
}
