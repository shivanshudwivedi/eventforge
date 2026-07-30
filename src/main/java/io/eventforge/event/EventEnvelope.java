package io.eventforge.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
        @NotNull UUID eventId,
        @NotBlank String eventType,
        @Positive int schemaVersion,
        @NotNull Instant occurredAt,
        @NotBlank String idempotencyKey,
        String traceId,
        @NotNull Map<String, Object> payload,
        Map<String, String> metadata) {

    public EventEnvelope {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
