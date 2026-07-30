package io.eventforge.event;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {

    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private final String topic;
    private final Timer publishTimer;

    public EventPublisher(
            KafkaTemplate<String, EventEnvelope> kafkaTemplate,
            @Value("${eventforge.topics.events}") String topic,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimer = meterRegistry.timer("eventforge.events.publish");
    }

    public void publish(EventEnvelope event) {
        publishTimer.record(() -> await(kafkaTemplate.send(topic, event.idempotencyKey(), event)));
    }

    public void publishBatch(List<EventEnvelope> events) {
        Timer.Sample sample = Timer.start();
        try {
            CompletableFuture<?>[] sends = events.stream()
                    .map(event -> kafkaTemplate.send(topic, event.idempotencyKey(), event))
                    .toArray(CompletableFuture[]::new);
            await(CompletableFuture.allOf(sends));
        } finally {
            sample.stop(publishTimer);
        }
    }

    private static void await(CompletableFuture<?> result) {
        try {
            result.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while publishing event", exception);
        } catch (Exception exception) {
            throw new EventPublishException("Kafka did not acknowledge the event", exception);
        }
    }
}
