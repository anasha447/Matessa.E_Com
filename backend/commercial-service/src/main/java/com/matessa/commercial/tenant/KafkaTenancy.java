package com.matessa.commercial.tenant;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Carries the store across Kafka: producers stamp {@link TenantContext#HEADER} on every
 * record ({@link #record}), and this interceptor (picked up by Boot for every
 * {@code @KafkaListener}) puts it in context for the listener, so the consumer reads and
 * writes the same store's schema the event came from.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTenancy {

    private static final Logger log = LoggerFactory.getLogger(KafkaTenancy.class);

    /** A record for {@code topic} tagged with the store currently in context. */
    public static ProducerRecord<String, String> record(String topic, String key, String value) {
        String tenant = TenantContext.getTenantId();
        if (tenant == null) {
            throw new IllegalStateException("Cannot publish to '" + topic + "' outside a store context");
        }
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add(TenantContext.HEADER, tenant.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Bean
    RecordInterceptor<Object, Object> tenantRecordInterceptor(TenantProperties tenants) {
        return new RecordInterceptor<>() {
            @Override
            public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                            Consumer<Object, Object> consumer) {
                Header header = record.headers().lastHeader(TenantContext.HEADER);
                String tenant = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
                if (!tenants.isKnown(tenant)) {
                    // Never guess a store: skipping beats writing into the wrong schema.
                    log.error("Skipping {}@{} offset {}: missing or unknown store header '{}'",
                            record.topic(), record.partition(), record.offset(), tenant);
                    return null;
                }
                TenantContext.setTenantId(tenant);
                return record;
            }

            @Override
            public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
                TenantContext.clear();
            }

            @Override
            public void clearThreadState(Consumer<?, ?> consumer) {
                TenantContext.clear();
            }
        };
    }
}
