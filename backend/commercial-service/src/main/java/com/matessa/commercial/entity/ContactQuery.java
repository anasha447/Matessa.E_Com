package com.matessa.commercial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A support message submitted from the storefront contact form. Guests may submit; staff
 *  (MANAGER/ADMIN) read and resolve it. */
@Entity
@Table(name = "contact_queries")
@Getter
@Setter
public class ContactQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "query_type", nullable = false)
    private String queryType;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactQueryStatus status = ContactQueryStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
