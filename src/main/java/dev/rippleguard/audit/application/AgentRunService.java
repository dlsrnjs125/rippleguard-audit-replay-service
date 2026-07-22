package dev.rippleguard.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rippleguard.audit.infrastructure.persistence.AgentAttemptAuditEntity;
import dev.rippleguard.audit.infrastructure.persistence.AgentAttemptAuditRepository;
import dev.rippleguard.audit.infrastructure.persistence.AgentRunAuditEntity;
import dev.rippleguard.audit.infrastructure.persistence.AgentRunAuditRepository;
import dev.rippleguard.audit.interfaces.rest.AgentAttemptResponse;
import dev.rippleguard.audit.interfaces.rest.AgentRunResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunService {
    private final AgentRunAuditRepository agentRuns;
    private final AgentAttemptAuditRepository attempts;
    private final ObjectMapper objectMapper;

    public AgentRunService(AgentRunAuditRepository agentRuns,
                           AgentAttemptAuditRepository attempts,
                           ObjectMapper objectMapper) {
        this.agentRuns = agentRuns;
        this.attempts = attempts;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AgentRunResponse get(UUID agentRunId) {
        AgentRunAuditEntity run = agentRuns.findById(agentRunId)
                .orElseThrow(() -> new NotFoundException("Agent run not found: " + agentRunId));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public List<AgentRunResponse> byEvaluationRun(UUID evaluationRunId) {
        return agentRuns.findByEvaluationRunIdOrderByValidatedAtAscAgentRunIdAsc(evaluationRunId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AgentRunResponse toResponse(AgentRunAuditEntity run) {
        return new AgentRunResponse(
                run.getAgentRunId(),
                run.getEvaluationRunId(),
                run.getDecisionCaseId(),
                run.getValidationOutcome(),
                reasonCodes(run.getValidationReasonCodes()),
                run.getAgentResultReference(),
                run.getAgentResultDigest(),
                run.getSnapshotId(),
                run.getSnapshotVersion(),
                run.getSnapshotSchemaVersion(),
                run.getSnapshotDigest(),
                run.getFeatureSchemaVersion(),
                run.getPreprocessingVersion(),
                run.getModelVersion(),
                run.getModelArtifactDigest(),
                run.getThresholdVersion(),
                run.getLatestAttemptId(),
                run.getAttemptCount(),
                run.getValidatedAt(),
                run.getSourceEventId(),
                run.getSourceSchemaVersion(),
                attempts.findByAgentRunIdOrderByAttemptIdAsc(run.getAgentRunId()).stream()
                        .map(this::toAttemptResponse)
                        .toList()
        );
    }

    private AgentAttemptResponse toAttemptResponse(AgentAttemptAuditEntity attempt) {
        return new AgentAttemptResponse(
                attempt.getAttemptId(),
                attempt.getAttemptStatus(),
                attempt.getFailureClassification(),
                attempt.getFailureReasonCode(),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                attempt.getResultDigest(),
                attempt.getSourceEventId()
        );
    }

    private List<String> reasonCodes(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored validation reason codes are not JSON", exception);
        }
    }
}
