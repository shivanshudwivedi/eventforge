package io.eventforge.event;

public final class UnsupportedSchemaException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    public UnsupportedSchemaException(String message) {
        super(message);
    }
}
