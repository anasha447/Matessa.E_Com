package com.matessa.order.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handling (Phase 5). A single {@link DefaultErrorHandler} bean is picked
 * up by every {@code @KafkaListener}:
 * <ul>
 *   <li><b>Transient</b> failures (DB hiccup, optimistic-lock clash) retry twice, 1 s apart,
 *       then go to the dead-letter topic — the idempotent consumer makes retries safe.</li>
 *   <li><b>Poison</b> messages (malformed JSON / deterministic errors) are <b>never retried</b>
 *       — straight to {@code <topic>.DLT} so they can't block the partition. This replaces the
 *       old silent {@code parse(...) -> null} drop, which lost bad messages with no trace.</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // Publishes the failed record to "<originalTopic>.DLT" (auto-created).
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                SerializationException.class,
                IllegalArgumentException.class);   // malformed payloads and records with no store (KafkaTenancy.MissingStoreException)
        return handler;
    }
}
