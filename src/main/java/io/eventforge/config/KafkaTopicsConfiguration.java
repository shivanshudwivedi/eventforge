package io.eventforge.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfiguration {

    @Bean
    NewTopic eventsTopic(EventForgeProperties properties) {
        return TopicBuilder.name(properties.topics().events())
                .partitions(properties.kafka().partitions())
                .replicas(properties.kafka().replicationFactor())
                .build();
    }
}
