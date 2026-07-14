package com.matessa.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Transactional Outbox row. Written in the SAME transaction as the business change, then
 * a poller publishes it to Kafka and stamps {@code publishedAt} — so an event is never
 * lost if the process crashes after commit but before publishing.
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;   // e.g. "Order"

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;     // e.g. the order number

    @Column(name = "event_type", nullable = false)
    private String eventType;       // e.g. "OrderPlaced"

    @Column(nullable = false)
    private String payload;         // JSON

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;    // null = not yet published
}
