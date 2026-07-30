package io.eventforge.processing;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.eventforge.event.EventEnvelope;
import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DeadLetterRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public DeadLetterRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void persist(EventEnvelope event, String sourceTopic, String error) {
        jdbc.sql("""
                        INSERT INTO dead_letter_events (
                            event_id, idempotency_key, event_type, schema_version,
                            source_topic, payload, error
                        ) VALUES (
                            :eventId, :idempotencyKey, :eventType, :schemaVersion,
                            :sourceTopic, CAST(:payload AS jsonb), :error
                        )
                        ON CONFLICT (event_id) DO UPDATE
                        SET error = EXCLUDED.error, failed_at = now()
                        """)
                .param("eventId", event.eventId())
                .param("idempotencyKey", event.idempotencyKey())
                .param("eventType", event.eventType())
                .param("schemaVersion", event.schemaVersion())
                .param("sourceTopic", sourceTopic)
                .param("payload", toJson(event.payload()), Types.VARCHAR)
                .param("error", error)
                .update();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Event contains invalid JSON", exception);
        }
    }
}
