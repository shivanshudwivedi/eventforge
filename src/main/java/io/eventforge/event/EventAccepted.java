package io.eventforge.event;

import java.util.UUID;

public record EventAccepted(UUID eventId, String status, String topic) {}
