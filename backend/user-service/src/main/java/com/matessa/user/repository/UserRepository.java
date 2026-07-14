package com.matessa.user.repository;

import com.matessa.user.entity.Role;
import com.matessa.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Admin directory search: optional role and/or enabled filters (null = ignore). */
    @Query(value = "select distinct u from User u left join u.roles r "
            + "where (:role is null or r = :role) and (:enabled is null or u.enabled = :enabled)",
            countQuery = "select count(distinct u) from User u left join u.roles r "
                    + "where (:role is null or r = :role) and (:enabled is null or u.enabled = :enabled)")
    Page<User> search(@Param("role") Role role, @Param("enabled") Boolean enabled, Pageable pageable);
}
