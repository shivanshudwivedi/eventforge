package io.eventforge.processing;

import io.eventforge.event.EventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {

    private final EventProcessor processor;
    private final DeadLetterRepository deadLetterRepository;
    private final Counter deadLetters;
    private final String sourceTopic;

    public EventConsumer(
            EventProcessor processor,
            DeadLetterRepository deadLetterRepository,
            MeterRegistry registry,
            @Value("${eventforge.topics.events}") String sourceTopic) {
        this.processor = processor;
        this.deadLetterRepository = deadLetterRepository;
        this.deadLetters = registry.counter("eventforge.events.dead_lettered");
        this.sourceTopic = sourceTopic;
    }

    @RetryableTopic(
            attempts = "${eventforge.retry.max-attempts}",
            backOff = @BackOff(
                    delayString = "${eventforge.retry.initial-delay-ms}",
                    multiplierString = "${eventforge.retry.multiplier}",
                    maxDelayString = "${eventforge.retry.max-delay-ms}"),
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "true",
            numPartitions = "${eventforge.kafka.partitions}",
            replicationFactor = "${eventforge.kafka.replication-factor}")
    @KafkaListener(
            topics = "${eventforge.topics.events}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${eventforge.kafka.partitions}")
    public void consume(ConsumerRecord<String, EventEnvelope> record) {
        processor.process(record);
    }

    @DltHandler
    public void handleDeadLetter(EventEnvelope event) {
        deadLetterRepository.persist(
                event,
                sourceTopic,
                "Retries exhausted; inspect Kafka dead-letter headers for the original exception");
        deadLetters.increment();
    }
}
