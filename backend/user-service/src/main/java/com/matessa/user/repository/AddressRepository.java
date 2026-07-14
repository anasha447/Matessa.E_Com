package com.matessa.user.repository;

import com.matessa.user.entity.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Page<Address> findByUserId(Long userId, Pageable pageable);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    List<Address> findByUserIdAndDefaultAddressTrue(Long userId);
}
