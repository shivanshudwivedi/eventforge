package io.eventforge.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventforge.event.EventEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessedEventRepositoryIT {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("eventforge")
                    .withUsername("eventforge")
                    .withPassword("eventforge");

    private JdbcClient jdbc;
    private ProcessedEventRepository repository;

    @BeforeAll
    void initializeDatabase() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        repository = new ProcessedEventRepository(jdbc, JsonMapper.builder().build());
    }

    @BeforeEach
    void clearTables() {
        jdbc.sql("TRUNCATE TABLE event_records, processed_events, dead_letter_events")
                .update();
    }

    @Test
    void duplicateIdempotencyKeyDoesNotCreateAnotherBusinessWrite() {
        UUID firstId = UUID.randomUUID();
        EventEnvelope first = event(firstId, "stable-key");
        EventEnvelope replayWithDifferentEventId = event(UUID.randomUUID(), "stable-key");

        assertThat(repository.persist(first, "eventforge.events", 0, 10))
                .isEqualTo(ProcessingResult.PROCESSED);
        assertThat(repository.persist(replayWithDifferentEventId, "eventforge.events", 0, 11))
                .isEqualTo(ProcessingResult.DUPLICATE);

        Integer processed = jdbc.sql("SELECT count(*) FROM processed_events")
                .query(Integer.class)
                .single();
        Integer writes = jdbc.sql("SELECT count(*) FROM event_records")
                .query(Integer.class)
                .single();
        assertThat(processed).isOne();
        assertThat(writes).isOne();
    }

    @Test
    void replayedKafkaOffsetIsIgnored() {
        EventEnvelope first = event(UUID.randomUUID(), "offset-key-one");
        EventEnvelope replay = event(UUID.randomUUID(), "offset-key-two");

        assertThat(repository.persist(first, "eventforge.events", 1, 99))
                .isEqualTo(ProcessingResult.PROCESSED);
        assertThat(repository.persist(replay, "eventforge.events", 1, 99))
                .isEqualTo(ProcessingResult.DUPLICATE);
    }

    private static EventEnvelope event(UUID id, String idempotencyKey) {
        return new EventEnvelope(
                id,
                "payment.captured",
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                idempotencyKey,
                "0123456789abcdef0123456789abcdef",
                Map.of("aggregateId", "payment-" + id),
                Map.of());
    }
}
