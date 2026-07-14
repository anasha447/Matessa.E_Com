package com.matessa.commercial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Event ids already consumed, so re-delivered Kafka messages (at-least-once) are ignored. */
@Entity
@Table(name = "processed_event")
@Getter
@Setter
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
