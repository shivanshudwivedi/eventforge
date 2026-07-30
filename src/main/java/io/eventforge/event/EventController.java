package io.eventforge.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventFactory eventFactory;
    private final EventPublisher publisher;
    private final EventQueryService queryService;
    private final String topic;

    public EventController(
            EventFactory eventFactory,
            EventPublisher publisher,
            EventQueryService queryService,
            @Value("${eventforge.topics.events}") String topic) {
        this.eventFactory = eventFactory;
        this.publisher = publisher;
        this.queryService = queryService;
        this.topic = topic;
    }

    @PostMapping
    ResponseEntity<EventAccepted> publish(@Valid @RequestBody EventSubmission submission) {
        EventEnvelope event = eventFactory.create(submission);
        publisher.publish(event);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/events/" + event.eventId()))
                .body(new EventAccepted(event.eventId(), "ACCEPTED", topic));
    }

    @PostMapping("/batch")
    ResponseEntity<List<EventAccepted>> publishBatch(
            @RequestBody @NotEmpty @Size(max = 1_000) List<@Valid EventSubmission> submissions) {
        List<EventEnvelope> events = submissions.stream().map(eventFactory::create).toList();
        publisher.publishBatch(events);
        return ResponseEntity.accepted()
                .body(events.stream()
                        .map(event -> new EventAccepted(event.eventId(), "ACCEPTED", topic))
                        .toList());
    }

    @GetMapping("/{eventId}")
    EventStatus get(@PathVariable UUID eventId) {
        return queryService.find(eventId);
    }
}
