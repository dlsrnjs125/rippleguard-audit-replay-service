package dev.rippleguard.audit.infrastructure.persistence;

import dev.rippleguard.audit.domain.AuditQuarantineReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event_quarantine")
public class AuditEventQuarantineEntity {
    @Id
    @Column(name = "quarantine_id")
    private UUID quarantineId;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "event_type", length = 128)
    private String eventType;

    @Column(name = "schema_version", length = 32)
    private String schemaVersion;

    @Column(length = 128)
    private String producer;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 128)
    private AuditQuarantineReason reasonCode;

    @Column(name = "safe_payload_hash", nullable = false, length = 64)
    private String safePayloadHash;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "retry_eligible", nullable = false)
    private boolean retryEligible;

    protected AuditEventQuarantineEntity() {
    }

    public AuditEventQuarantineEntity(UUID quarantineId, UUID sourceEventId, String eventType, String schemaVersion,
                                      String producer, Instant receivedAt, AuditQuarantineReason reasonCode,
                                      String safePayloadHash, String correlationId, UUID causationId,
                                      boolean retryEligible) {
        this.quarantineId = quarantineId;
        this.sourceEventId = sourceEventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.producer = producer;
        this.receivedAt = receivedAt;
        this.reasonCode = reasonCode;
        this.safePayloadHash = safePayloadHash;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.retryEligible = retryEligible;
    }
}
