package io.eventforge.processing;

import io.eventforge.config.EventForgeProperties;
import io.eventforge.event.EventEnvelope;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class FailureInjector {

    private static final String FAILURE_COUNT_METADATA = "injectFailures";

    private final StringRedisTemplate redis;
    private final EventForgeProperties properties;

    public FailureInjector(StringRedisTemplate redis, EventForgeProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void failIfRequested(EventEnvelope event) {
        if (!properties.failureInjection().enabled()) {
            return;
        }
        int requestedFailures = parseFailureCount(event);
        if (requestedFailures == 0) {
            return;
        }
        String key = properties.failureInjection().redisPrefix() + event.eventId();
        Long attempt = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofHours(1));
        if (attempt != null && attempt <= requestedFailures) {
            throw new InjectedTransientFailure(
                    "Injected transient failure %d/%d".formatted(attempt, requestedFailures));
        }
    }

    private static int parseFailureCount(EventEnvelope event) {
        String value = event.metadata().get(FAILURE_COUNT_METADATA);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("injectFailures metadata must be an integer", exception);
        }
    }
}
