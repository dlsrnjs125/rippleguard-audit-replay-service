package dev.rippleguard.audit.interfaces.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentRunResponse(UUID agentRunId,
                               UUID evaluationRunId,
                               String decisionCaseId,
                               String validationOutcome,
                               List<String> validationReasonCodes,
                               String agentResultReference,
                               String agentResultDigest,
                               String snapshotId,
                               String snapshotVersion,
                               String snapshotSchemaVersion,
                               String snapshotDigest,
                               String featureSchemaVersion,
                               String preprocessingVersion,
                               String modelVersion,
                               String modelArtifactDigest,
                               String thresholdVersion,
                               int latestAttemptId,
                               int attemptCount,
                               Instant validatedAt,
                               UUID sourceEventId,
                               String sourceSchemaVersion,
                               List<AgentAttemptResponse> attempts) {
}
