package dev.rippleguard.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_run_audit")
public class AgentRunAuditEntity {
    @Id
    @Column(name = "agent_run_id")
    private UUID agentRunId;

    @Column(name = "decision_case_id", nullable = false, length = 128)
    private String decisionCaseId;

    @Column(name = "evaluation_run_id", nullable = false)
    private UUID evaluationRunId;

    @Column(name = "validation_outcome", nullable = false, length = 32)
    private String validationOutcome;

    @Column(name = "validation_reason_codes", nullable = false, columnDefinition = "text")
    private String validationReasonCodes;

    @Column(name = "agent_result_reference", nullable = false, length = 512)
    private String agentResultReference;

    @Column(name = "agent_result_digest", nullable = false, length = 71)
    private String agentResultDigest;

    @Column(name = "validated_schema_version", nullable = false, length = 32)
    private String validatedSchemaVersion;

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    @Column(name = "latest_attempt_id", nullable = false)
    private int latestAttemptId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "first_occurred_at", nullable = false)
    private Instant firstOccurredAt;

    @Column(name = "last_occurred_at", nullable = false)
    private Instant lastOccurredAt;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "source_schema_version", nullable = false, length = 32)
    private String sourceSchemaVersion;

    @Column(name = "ingestion_status", nullable = false, length = 32)
    private String ingestionStatus;

    @Column(name = "snapshot_id", length = 128)
    private String snapshotId;

    @Column(name = "snapshot_version", length = 128)
    private String snapshotVersion;

    @Column(name = "snapshot_schema_version", length = 32)
    private String snapshotSchemaVersion;

    @Column(name = "snapshot_digest", length = 71)
    private String snapshotDigest;

    @Column(name = "feature_schema_version", length = 128)
    private String featureSchemaVersion;

    @Column(name = "preprocessing_version", length = 128)
    private String preprocessingVersion;

    @Column(name = "model_version", length = 128)
    private String modelVersion;

    @Column(name = "model_artifact_digest", length = 71)
    private String modelArtifactDigest;

    @Column(name = "threshold_version", length = 128)
    private String thresholdVersion;

    protected AgentRunAuditEntity() {
    }

    public AgentRunAuditEntity(UUID agentRunId, String decisionCaseId, UUID evaluationRunId,
                               String validationOutcome, String validationReasonCodes,
                               String agentResultReference, String agentResultDigest,
                               String validatedSchemaVersion, Instant validatedAt, int latestAttemptId,
                               Instant occurredAt, UUID sourceEventId, String sourceSchemaVersion) {
        this.agentRunId = agentRunId;
        this.decisionCaseId = decisionCaseId;
        this.evaluationRunId = evaluationRunId;
        this.validationOutcome = validationOutcome;
        this.validationReasonCodes = validationReasonCodes;
        this.agentResultReference = agentResultReference;
        this.agentResultDigest = agentResultDigest;
        this.validatedSchemaVersion = validatedSchemaVersion;
        this.validatedAt = validatedAt;
        this.latestAttemptId = latestAttemptId;
        this.attemptCount = latestAttemptId;
        this.firstOccurredAt = occurredAt;
        this.lastOccurredAt = occurredAt;
        this.sourceEventId = sourceEventId;
        this.sourceSchemaVersion = sourceSchemaVersion;
        this.ingestionStatus = "ACCEPTED";
    }

    public UUID getAgentRunId() {
        return agentRunId;
    }

    public String getDecisionCaseId() {
        return decisionCaseId;
    }

    public UUID getEvaluationRunId() {
        return evaluationRunId;
    }

    public String getValidationOutcome() {
        return validationOutcome;
    }

    public String getValidationReasonCodes() {
        return validationReasonCodes;
    }

    public String getAgentResultReference() {
        return agentResultReference;
    }

    public String getAgentResultDigest() {
        return agentResultDigest;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public int getLatestAttemptId() {
        return latestAttemptId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getSourceSchemaVersion() {
        return sourceSchemaVersion;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getSnapshotVersion() {
        return snapshotVersion;
    }

    public String getSnapshotSchemaVersion() {
        return snapshotSchemaVersion;
    }

    public String getSnapshotDigest() {
        return snapshotDigest;
    }

    public String getFeatureSchemaVersion() {
        return featureSchemaVersion;
    }

    public String getPreprocessingVersion() {
        return preprocessingVersion;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getModelArtifactDigest() {
        return modelArtifactDigest;
    }

    public String getThresholdVersion() {
        return thresholdVersion;
    }
}
