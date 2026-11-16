package com.matessa.order.messaging;

import com.matessa.order.entity.OutboxEvent;
import com.matessa.order.repository.OutboxRepository;
import com.matessa.order.tenant.KafkaTenancy;
import com.matessa.order.tenant.TenantContext;
import com.matessa.order.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes unpublished outbox rows to Kafka, then stamps publishedAt (transactional outbox).
 *
 * <p><b>Safe with several replicas.</b> Rows are claimed with
 * {@code SELECT … FOR UPDATE SKIP LOCKED} inside a short transaction: each replica takes a
 * different batch, and a row in flight on one replica is invisible to the others. (Before, every
 * replica selected the same 50 rows and published each of them N times.)
 *
 * <p>The batch is sent as a whole and awaited once, rather than one blocking send per row.
 * At-least-once: a crash after sending but before the commit re-sends the batch next run —
 * consumers de-duplicate on eventId (processed_event). The topic is the event type; the key
 * is the aggregate id, so one aggregate's events stay ordered within a partition.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH = 50;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TenantProperties tenants;
    private final TransactionTemplate transactions;

    public OutboxPoller(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
                        TenantProperties tenants, TransactionTemplate transactions) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tenants = tenants;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    public void publishPending() {
        // Each store has its own outbox table (its own schema): drain them one at a time.
        tenants.tenants().forEach(store -> TenantContext.runAs(store, this::publishPendingForCurrentStore));
    }

    private void publishPendingForCurrentStore() {
        transactions.executeWithoutResult(tx -> {
            List<OutboxEvent> batch = outboxRepository.claimUnpublished(BATCH);
            if (batch.isEmpty()) {
                return;
            }
            List<CompletableFuture<SendResult<String, String>>> sends = new ArrayList<>(batch.size());
            for (OutboxEvent event : batch) {
                sends.add(kafkaTemplate.send(KafkaTenancy.record(event.getEventType(), event.getAggregateId(), event.getPayload())));
            }
            Instant now = Instant.now();
            for (int i = 0; i < batch.size(); i++) {
                OutboxEvent event = batch.get(i);
                try {
                    sends.get(i).get(10, TimeUnit.SECONDS);
                    event.setPublishedAt(now);
                } catch (Exception ex) {
                    // leave publishedAt null so it is retried; don't fail the rest of the batch
                    log.warn("Failed to publish outbox event {} ({}); will retry next run: {}",
                            event.getId(), event.getEventType(), ex.toString());
                }
            }
            // publishedAt changes are flushed on commit, which also releases the row locks.
        });
    }
}
