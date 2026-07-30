package io.eventforge;

import io.eventforge.config.EventForgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EventForgeProperties.class)
public class EventForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventForgeApplication.class, args);
    }
}
