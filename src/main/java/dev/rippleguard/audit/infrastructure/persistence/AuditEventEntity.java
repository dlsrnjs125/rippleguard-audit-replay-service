package dev.rippleguard.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "producer", nullable = false, length = 128)
    private String producer;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "case_id", nullable = false, length = 128)
    private String caseId;

    @Column(name = "evaluation_run_id")
    private UUID evaluationRunId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "sanitized_payload", nullable = false, columnDefinition = "text")
    private String sanitizedPayload;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "unknown_version", nullable = false)
    private boolean unknownVersion;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(UUID eventId, String eventType, String schemaVersion, Instant occurredAt, String producer,
                            UUID applicationId, String caseId, UUID evaluationRunId, String correlationId,
                            UUID causationId, String sanitizedPayload, String payloadHash, boolean unknownVersion,
                            Instant ingestedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.occurredAt = occurredAt;
        this.producer = producer;
        this.applicationId = applicationId;
        this.caseId = caseId;
        this.evaluationRunId = evaluationRunId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.sanitizedPayload = sanitizedPayload;
        this.payloadHash = payloadHash;
        this.unknownVersion = unknownVersion;
        this.ingestedAt = ingestedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getProducer() {
        return producer;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getCaseId() {
        return caseId;
    }

    public UUID getEvaluationRunId() {
        return evaluationRunId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public UUID getCausationId() {
        return causationId;
    }

    public String getSanitizedPayload() {
        return sanitizedPayload;
    }

    public boolean isUnknownVersion() {
        return unknownVersion;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
