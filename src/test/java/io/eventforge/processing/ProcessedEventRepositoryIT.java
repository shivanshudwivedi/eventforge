package io.eventforge.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventforge.event.EventEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
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

    private AnnotationConfigApplicationContext context;
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
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcClient.class, () -> jdbc);
        context.registerBean(tools.jackson.databind.ObjectMapper.class,
                () -> JsonMapper.builder().build());
        context.registerBean(PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(ProcessedEventRepository.class);
        context.refresh();
        repository = context.getBean(ProcessedEventRepository.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }

    @AfterAll
    void closeContext() {
        if (context != null) {
            context.close();
        }
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

    @Test
    void repeatedEventIdDoesNotCreateAnotherBusinessWrite() {
        UUID eventId = UUID.randomUUID();
        assertThat(repository.persist(event(eventId, "identity-one"), "events", 0, 1))
                .isEqualTo(ProcessingResult.PROCESSED);
        assertThat(repository.persist(event(eventId, "identity-two"), "events", 0, 2))
                .isEqualTo(ProcessingResult.DUPLICATE);
        assertThat(jdbc.sql("SELECT count(*) FROM event_records")
                .query(Integer.class).single()).isOne();
    }

    @Test
    void businessWriteFailureRollsBackClaimAndAllowsCorrectedReplay() {
        UUID eventId = UUID.randomUUID();
        EventEnvelope valid = event(eventId, "rollback-key");
        EventEnvelope invalid = new EventEnvelope(
                valid.eventId(), valid.eventType(), valid.schemaVersion(), valid.occurredAt(),
                valid.idempotencyKey(), valid.traceId(), Map.of(), Map.of());
        assertThatThrownBy(() -> repository.persist(invalid, "events", 0, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM processed_events")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM event_records")
                .query(Integer.class).single()).isZero();
        assertThat(repository.persist(valid, "events", 0, 1))
                .isEqualTo(ProcessingResult.PROCESSED);
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
