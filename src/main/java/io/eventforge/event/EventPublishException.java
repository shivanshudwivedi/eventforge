package io.eventforge.event;

public class EventPublishException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
