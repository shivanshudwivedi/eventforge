package io.eventforge.processing;

public class InjectedTransientFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InjectedTransientFailure(String message) {
        super(message);
    }
}
