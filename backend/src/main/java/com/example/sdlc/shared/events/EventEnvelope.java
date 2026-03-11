package com.example.sdlc.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope for domain events with metadata.
 * @param <T> the event payload type
 */
public record EventEnvelope<T>(
    String eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    Instant timestamp,
    T payload
) {
    public static <T> EventEnvelope<T> of(
        String aggregateType,
        String aggregateId,
        String eventType,
        T payload
    ) {
        return new EventEnvelope<>(
            UUID.randomUUID().toString(),
            eventType,
            aggregateType,
            aggregateId,
            Instant.now(),
            payload
        );
    }
}
