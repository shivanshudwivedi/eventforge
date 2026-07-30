package io.eventforge.event;

import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class EventQueryService {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public EventQueryService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public EventStatus find(UUID eventId) {
        return jdbc.sql("""
                        SELECT p.event_id, p.idempotency_key, p.event_type, p.schema_version,
                               p.processed_at, r.payload::text AS payload
                        FROM processed_events p
                        JOIN event_records r ON r.event_id = p.event_id
                        WHERE p.event_id = :eventId
                        """)
                .param("eventId", eventId)
                .query((rs, rowNum) -> new EventStatus(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("idempotency_key"),
                        rs.getString("event_type"),
                        rs.getInt("schema_version"),
                        "PROCESSED",
                        rs.getTimestamp("processed_at").toInstant(),
                        objectMapper.readTree(rs.getString("payload")),
                        null))
                .optional()
                .or(() -> findDeadLetter(eventId))
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    private Optional<EventStatus> findDeadLetter(UUID eventId) {
        return jdbc.sql("""
                        SELECT event_id, idempotency_key, event_type, schema_version,
                               failed_at, payload::text AS payload, error
                        FROM dead_letter_events
                        WHERE event_id = :eventId
                        """)
                .param("eventId", eventId)
                .query((rs, rowNum) -> new EventStatus(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("idempotency_key"),
                        rs.getString("event_type"),
                        rs.getInt("schema_version"),
                        "DEAD_LETTERED",
                        rs.getTimestamp("failed_at").toInstant(),
                        objectMapper.readTree(rs.getString("payload")),
                        rs.getString("error")))
                .optional();
    }
}
