package com.matessa.order.messaging;

import com.matessa.order.entity.OutboxEvent;
import com.matessa.order.repository.OutboxRepository;
import com.matessa.order.tenant.KafkaTenancy;
import com.matessa.order.tenant.TenantContext;
import com.matessa.order.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Publishes unpublished outbox rows to Kafka on a fixed delay, then stamps publishedAt.
 * At-least-once: if it crashes after sending but before stamping, the row is re-sent next
 * run — consumers are idempotent (processed_event), so duplicates are safe. The topic is
 * the outbox event type; the key is the aggregate id (order number) for ordering per order.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TenantProperties tenants;

    public OutboxPoller(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
                        TenantProperties tenants) {
        this.tenants = tenants;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    public void publishPending() {
        // Each store has its own outbox table (its own schema): drain them one at a time.
        tenants.tenants().forEach(store -> TenantContext.runAs(store, this::publishPendingForCurrentStore));
    }

    private void publishPendingForCurrentStore() {
        for (OutboxEvent event : outboxRepository.findTop50ByPublishedAtIsNullOrderByIdAsc()) {
            try {
                kafkaTemplate.send(KafkaTenancy.record(event.getEventType(), event.getAggregateId(), event.getPayload()))
                        .get(10, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception ex) {
                log.warn("Failed to publish outbox event {} ({}); will retry next run: {}",
                        event.getId(), event.getEventType(), ex.toString());
                // leave publishedAt null so it is retried; don't fail the rest of the batch
            }
        }
    }
}
