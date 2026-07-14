package com.matessa.commercial.repository;

import com.matessa.commercial.entity.ContactQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactQueryRepository extends JpaRepository<ContactQuery, Long> {

    Page<ContactQuery> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
