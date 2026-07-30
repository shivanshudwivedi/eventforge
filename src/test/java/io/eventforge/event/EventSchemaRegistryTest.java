package io.eventforge.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventSchemaRegistryTest {

    private final EventSchemaRegistry registry = new EventSchemaRegistry();

    @Test
    void upcastsVersionOneOrderToCurrentSchema() {
        EventEnvelope event = event("order.created", 1, Map.of(
                "aggregateId", "order-42",
                "customerId", "customer-7",
                "amount", 129.99));

        assertThat(registry.upcast(event))
                .containsEntry("currency", "USD")
                .containsEntry("aggregateId", "order-42");
        assertThat(registry.currentVersion("order.created")).isEqualTo(2);
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        EventEnvelope event = event("order.created", 99, Map.of(
                "aggregateId", "order-42",
                "customerId", "customer-7",
                "amount", 10));

        assertThatThrownBy(() -> registry.validate(event))
                .isInstanceOf(UnsupportedSchemaException.class)
                .hasMessageContaining("order.created v99");
    }

    @Test
    void rejectsMissingRequiredFields() {
        EventEnvelope event =
                event("payment.captured", 1, Map.of("amount", 10));

        assertThatThrownBy(() -> registry.validate(event))
                .isInstanceOf(UnsupportedSchemaException.class)
                .hasMessageContaining("aggregateId");
    }

    private static EventEnvelope event(
            String eventType,
            int version,
            Map<String, Object> payload) {
        UUID id = UUID.randomUUID();
        return new EventEnvelope(
                id,
                eventType,
                version,
                Instant.parse("2026-01-01T00:00:00Z"),
                id.toString(),
                "0123456789abcdef0123456789abcdef",
                payload,
                Map.of());
    }
}
