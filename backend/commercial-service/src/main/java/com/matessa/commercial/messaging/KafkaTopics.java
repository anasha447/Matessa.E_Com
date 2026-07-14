package com.matessa.commercial.messaging;

/** Kafka topic names (also used as the outbox event type). */
public final class KafkaTopics {

    public static final String ORDER_PLACED = "OrderPlaced";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String ORDER_REJECTED = "OrderRejected";

    private KafkaTopics() {
    }
}
