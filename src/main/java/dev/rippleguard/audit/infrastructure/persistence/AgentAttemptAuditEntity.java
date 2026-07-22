package dev.rippleguard.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "agent_attempt_audit")
@IdClass(AgentAttemptAuditEntity.Key.class)
public class AgentAttemptAuditEntity {
    @Id
    @Column(name = "agent_run_id")
    private UUID agentRunId;

    @Id
    @Column(name = "attempt_id")
    private int attemptId;

    @Id
    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "attempt_status", nullable = false, length = 32)
    private String attemptStatus;

    @Column(name = "failure_classification", length = 64)
    private String failureClassification;

    @Column(name = "failure_reason_code", length = 128)
    private String failureReasonCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "result_digest", nullable = false, length = 71)
    private String resultDigest;

    protected AgentAttemptAuditEntity() {
    }

    public AgentAttemptAuditEntity(UUID agentRunId, int attemptId, UUID sourceEventId, String attemptStatus,
                                   String resultDigest, Instant completedAt) {
        this.agentRunId = agentRunId;
        this.attemptId = attemptId;
        this.sourceEventId = sourceEventId;
        this.attemptStatus = attemptStatus;
        this.resultDigest = resultDigest;
        this.completedAt = completedAt;
    }

    public int getAttemptId() {
        return attemptId;
    }

    public String getAttemptStatus() {
        return attemptStatus;
    }

    public String getFailureClassification() {
        return failureClassification;
    }

    public String getFailureReasonCode() {
        return failureReasonCode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getResultDigest() {
        return resultDigest;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public static class Key implements Serializable {
        private UUID agentRunId;
        private int attemptId;
        private UUID sourceEventId;

        public Key() {
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return attemptId == key.attemptId
                    && Objects.equals(agentRunId, key.agentRunId)
                    && Objects.equals(sourceEventId, key.sourceEventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agentRunId, attemptId, sourceEventId);
        }
    }
}
