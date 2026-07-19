package dev.rippleguard.audit.interfaces.rest;

import dev.rippleguard.audit.domain.TimelineEventStatus;
import java.time.Instant;
import java.util.UUID;

public record TimelineEventResponse(UUID eventId,
                                    String eventType,
                                    String caseId,
                                    Instant occurredAt,
                                    String producer,
                                    UUID evaluationRunId,
                                    String correlationId,
                                    UUID causationId,
                                    TimelineEventStatus status,
                                    String summary) {
}
