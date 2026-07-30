package io.eventforge.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class EventSchemaRegistry {

    private static final Map<String, Set<Integer>> SUPPORTED =
            Map.of(
                    "order.created", Set.of(1, 2),
                    "order.cancelled", Set.of(1),
                    "payment.captured", Set.of(1));

    public void validate(EventEnvelope event) {
        var versions = SUPPORTED.get(event.eventType());
        if (versions == null || !versions.contains(event.schemaVersion())) {
            throw new UnsupportedSchemaException(
                    "Unsupported event schema: %s v%d"
                            .formatted(event.eventType(), event.schemaVersion()));
        }
        validateRequiredFields(event.eventType(), event.payload());
    }

    public Map<String, Object> upcast(EventEnvelope event) {
        var payload = new HashMap<>(event.payload());
        if ("order.created".equals(event.eventType()) && event.schemaVersion() == 1) {
            payload.putIfAbsent("currency", "USD");
        }
        return Map.copyOf(payload);
    }

    private void validateRequiredFields(String eventType, Map<String, Object> payload) {
        requireText(payload, "aggregateId");
        if ("order.created".equals(eventType)) {
            requireText(payload, "customerId");
            if (!(payload.get("amount") instanceof Number)) {
                throw new UnsupportedSchemaException("order.created payload requires numeric amount");
            }
        }
    }

    public Map<String, Object> validateAndUpcast(
            String eventType,
            int schemaVersion,
            Map<String, Object> payload) {
        var temporary = new EventEnvelope(
                java.util.UUID.randomUUID(),
                eventType,
                schemaVersion,
                java.time.Instant.EPOCH,
                "schema-validation",
                "schema-validation",
                payload,
                Map.of());
        validate(temporary);
        return upcast(temporary);
    }

    public int currentVersion(String eventType) {
        var versions = SUPPORTED.get(eventType);
        if (versions == null) {
            throw new UnsupportedSchemaException("Unsupported event type: " + eventType);
        }
        return versions.stream().mapToInt(Integer::intValue).max().orElseThrow();
    }

    private void requireText(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String value) || value.isBlank()) {
            throw new UnsupportedSchemaException("payload requires text field: " + field);
        }
    }
}
