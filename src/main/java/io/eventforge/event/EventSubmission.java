package io.eventforge.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventSubmission(
        UUID eventId,
        @NotBlank String eventType,
        @Positive int schemaVersion,
        Instant occurredAt,
        @NotBlank String idempotencyKey,
        @NotNull Map<String, Object> payload,
        Map<String, String> metadata) {}
