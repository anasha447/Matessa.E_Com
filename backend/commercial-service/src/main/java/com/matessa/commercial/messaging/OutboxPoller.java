package com.matessa.commercial.messaging;

import com.matessa.commercial.entity.OutboxEvent;
import com.matessa.commercial.repository.OutboxRepository;
import com.matessa.commercial.tenant.KafkaTenancy;
import com.matessa.commercial.tenant.TenantContext;
import com.matessa.commercial.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Publishes unpublished outbox rows to Kafka on a fixed delay (commercial emits
 * OrderRejected this way). At-least-once; consumers are idempotent.
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
            }
        }
    }
}
