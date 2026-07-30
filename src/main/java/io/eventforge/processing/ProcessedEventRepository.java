package io.eventforge.processing;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.eventforge.event.EventEnvelope;
import java.sql.Types;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProcessedEventRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ProcessedEventRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProcessingResult persist(
            EventEnvelope event,
            String topic,
            int partition,
            long offset) {
        int claimed = jdbc.sql("""
                        INSERT INTO processed_events (
                            event_id, idempotency_key, event_type, schema_version,
                            topic, partition_id, kafka_offset, trace_id
                        ) VALUES (
                            :eventId, :idempotencyKey, :eventType, :schemaVersion,
                            :topic, :partition, :offset, :traceId
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .param("eventId", event.eventId())
                .param("idempotencyKey", event.idempotencyKey())
                .param("eventType", event.eventType())
                .param("schemaVersion", event.schemaVersion())
                .param("topic", topic)
                .param("partition", partition)
                .param("offset", offset)
                .param("traceId", event.traceId())
                .update();
        if (claimed == 0) {
            return ProcessingResult.DUPLICATE;
        }

        jdbc.sql("""
                        INSERT INTO event_records (
                            event_id, aggregate_id, event_type, schema_version,
                            occurred_at, payload, metadata
                        ) VALUES (
                            :eventId, :aggregateId, :eventType, :schemaVersion,
                            :occurredAt, CAST(:payload AS jsonb), CAST(:metadata AS jsonb)
                        )
                        """)
                .param("eventId", event.eventId())
                .param("aggregateId", (String) event.payload().get("aggregateId"))
                .param("eventType", event.eventType())
                .param("schemaVersion", event.schemaVersion())
                .param("occurredAt", event.occurredAt().atOffset(ZoneOffset.UTC))
                .param("payload", toJson(event.payload()), Types.VARCHAR)
                .param("metadata", toJson(event.metadata()), Types.VARCHAR)
                .update();
        return ProcessingResult.PROCESSED;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Event contains invalid JSON", exception);
        }
    }
}
