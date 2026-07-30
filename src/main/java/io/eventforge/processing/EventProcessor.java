package io.eventforge.processing;

import io.eventforge.event.EventEnvelope;
import io.eventforge.event.EventSchemaRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;

@Service
public class EventProcessor {

    private final EventSchemaRegistry schemas;
    private final FailureInjector failureInjector;
    private final ProcessedEventRepository repository;
    private final Counter processed;
    private final Counter duplicates;
    private final Timer processingTimer;

    public EventProcessor(
            EventSchemaRegistry schemas,
            FailureInjector failureInjector,
            ProcessedEventRepository repository,
            MeterRegistry registry) {
        this.schemas = schemas;
        this.failureInjector = failureInjector;
        this.repository = repository;
        this.processed = registry.counter("eventforge.events.processed");
        this.duplicates = registry.counter("eventforge.events.duplicates");
        this.processingTimer = registry.timer("eventforge.events.processing");
    }

    public void process(ConsumerRecord<String, EventEnvelope> record) {
        processingTimer.record(() -> {
            EventEnvelope event = record.value();
            schemas.validate(event);
            failureInjector.failIfRequested(event);
            ProcessingResult result =
                    repository.persist(event, record.topic(), record.partition(), record.offset());
            if (result == ProcessingResult.PROCESSED) {
                processed.increment();
            } else {
                duplicates.increment();
            }
        });
    }
}
