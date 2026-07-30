package io.eventforge.event;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventNotFoundException(UUID eventId) {
        super("Event " + eventId + " has not been processed or dead-lettered");
    }
}
