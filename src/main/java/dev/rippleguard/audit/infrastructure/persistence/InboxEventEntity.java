package dev.rippleguard.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_event")
public class InboxEventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected InboxEventEntity() {
    }

    public InboxEventEntity(UUID eventId, String eventType, UUID applicationId, String payloadHash, Instant seenAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.applicationId = applicationId;
        this.payloadHash = payloadHash;
        this.duplicateCount = 0;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
    }

    public void markDuplicate(Instant seenAt) {
        this.duplicateCount++;
        this.lastSeenAt = seenAt;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }
}
