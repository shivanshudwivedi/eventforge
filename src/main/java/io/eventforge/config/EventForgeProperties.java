package io.eventforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eventforge")
public record EventForgeProperties(
        String apiKey,
        Topics topics,
        Kafka kafka,
        Retry retry,
        FailureInjection failureInjection) {

    public record Topics(String events) {}

    public record Kafka(int partitions, short replicationFactor) {}

    public record Retry(int maxAttempts, long initialDelayMs, double multiplier, long maxDelayMs) {}

    public record FailureInjection(boolean enabled, String redisPrefix) {}
}
