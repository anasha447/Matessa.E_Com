package com.matessa.user.repository;

import com.matessa.user.entity.Role;
import com.matessa.user.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Read-only aggregate queries for the customer analytics dashboard (ADMIN only). */
public interface UserAnalyticsRepository extends Repository<User, Long> {

    @Query("select count(u) from User u")
    long totalUsers();

    @Query("select count(u) from User u where u.createdAt >= :from")
    long newSince(@Param("from") Instant from);

    @Query("select count(distinct u) from User u join u.roles r where r = :role")
    long countByRole(@Param("role") Role role);

    @Query("select r, count(distinct u) from User u join u.roles r group by r")
    List<Object[]> countGroupedByRole();
}
