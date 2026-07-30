package io.eventforge.event;

import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EventFactory {

    private final EventSchemaRegistry schemas;
    private final Tracer tracer;

    public EventFactory(EventSchemaRegistry schemas, Tracer tracer) {
        this.schemas = schemas;
        this.tracer = tracer;
    }

    public EventEnvelope create(EventSubmission submission) {
        UUID eventId = submission.eventId() == null ? UUID.randomUUID() : submission.eventId();
        String idempotencyKey = submission.idempotencyKey() == null
                ? eventId.toString()
                : submission.idempotencyKey();
        var payload = schemas.validateAndUpcast(
                submission.eventType(),
                submission.schemaVersion(),
                submission.payload());
        String traceId = tracer.currentSpan() == null
                ? eventId.toString().replace("-", "")
                : tracer.currentSpan().context().traceId();
        return new EventEnvelope(
                eventId,
                submission.eventType(),
                schemas.currentVersion(submission.eventType()),
                submission.occurredAt() == null
                        ? Instant.now(Clock.systemUTC())
                        : submission.occurredAt(),
                idempotencyKey,
                traceId,
                payload,
                submission.metadata() == null ? Map.of() : submission.metadata());
    }
}
