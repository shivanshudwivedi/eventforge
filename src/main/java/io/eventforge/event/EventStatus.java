package io.eventforge.event;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventStatus(
        UUID eventId,
        String idempotencyKey,
        String eventType,
        int schemaVersion,
        String status,
        Instant processedAt,
        JsonNode payload,
        String error) {
}
