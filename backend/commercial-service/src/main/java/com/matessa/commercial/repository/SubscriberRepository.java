package com.matessa.commercial.repository;

import com.matessa.commercial.entity.Subscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    Optional<Subscriber> findByEmail(String email);

    Page<Subscriber> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
