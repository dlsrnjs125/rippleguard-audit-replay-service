package dev.rippleguard.audit.interfaces.rest;

import java.time.Instant;
import java.util.UUID;

public record AgentAttemptResponse(int attemptId,
                                   String attemptStatus,
                                   String failureClassification,
                                   String failureReasonCode,
                                   Instant startedAt,
                                   Instant completedAt,
                                   String resultDigest,
                                   UUID sourceEventId) {
}
