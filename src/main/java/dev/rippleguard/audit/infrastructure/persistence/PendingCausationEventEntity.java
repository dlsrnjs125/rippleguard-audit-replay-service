package dev.rippleguard.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_causation_event")
public class PendingCausationEventEntity {
    public static final String PENDING = "PENDING_CAUSATION";
    public static final String RESOLVED = "RESOLVED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(nullable = false, length = 128)
    private String producer;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "case_id", nullable = false, length = 128)
    private String caseId;

    @Column(name = "evaluation_run_id")
    private UUID evaluationRunId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "causation_id", nullable = false)
    private UUID causationId;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
    private String rawPayload;

    @Column(name = "raw_payload_hash", nullable = false, length = 64)
    private String rawPayloadHash;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 32)
    private String status;

    protected PendingCausationEventEntity() {
    }

    public PendingCausationEventEntity(UUID eventId, String eventType, String schemaVersion, String producer,
                                       UUID applicationId, String caseId, UUID evaluationRunId, String correlationId,
                                       UUID causationId, String rawPayload, String rawPayloadHash, Instant firstSeenAt,
                                       Instant nextAttemptAt, int attemptCount, Instant expiresAt, String status) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.producer = producer;
        this.applicationId = applicationId;
        this.caseId = caseId;
        this.evaluationRunId = evaluationRunId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.rawPayload = rawPayload;
        this.rawPayloadHash = rawPayloadHash;
        this.firstSeenAt = firstSeenAt;
        this.nextAttemptAt = nextAttemptAt;
        this.attemptCount = attemptCount;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCausationId() {
        return causationId;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getRawPayloadHash() {
        return rawPayloadHash;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void markAttempt(Instant nextAttemptAt) {
        this.attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markResolved() {
        this.status = RESOLVED;
    }

    public void markExpired() {
        this.status = EXPIRED;
    }
}
