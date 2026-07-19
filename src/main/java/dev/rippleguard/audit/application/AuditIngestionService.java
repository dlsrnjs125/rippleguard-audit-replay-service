package dev.rippleguard.audit.application;

import dev.rippleguard.audit.infrastructure.persistence.AuditEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventRepository;
import dev.rippleguard.audit.infrastructure.persistence.InboxEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.InboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditIngestionService {
    private static final Logger log = LoggerFactory.getLogger(AuditIngestionService.class);
    private static final String SUPPORTED_SCHEMA_VERSION = "1.1.0";
    private static final Set<String> PHASE_1_EVENTS = Set.of(
            "loan.application.submitted.v1",
            "governance.review.started.v1",
            "agent.evaluation.requested.v1",
            "agent.evaluation.completed.v1",
            "loan.decision.commanded.v1",
            "loan.decision.finalized.v1"
    );

    private final AuditEventRepository auditEvents;
    private final InboxEventRepository inbox;
    private final JsonSupport json;
    private final PayloadSanitizer sanitizer;
    private final Clock clock;

    public AuditIngestionService(AuditEventRepository auditEvents,
                                 InboxEventRepository inbox,
                                 JsonSupport json,
                                 PayloadSanitizer sanitizer,
                                 Clock clock) {
        this.auditEvents = auditEvents;
        this.inbox = inbox;
        this.json = json;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    public void ingestRaw(String rawMessage) {
        ingest(json.eventEnvelope(rawMessage));
    }

    @Transactional
    public void ingest(EventEnvelope event) {
        Instant now = clock.instant();
        String payloadHash = json.sha256(event.payload() == null ? "" : event.payload().toString());
        var existingInbox = inbox.findById(event.eventId());
        if (existingInbox.isPresent()) {
            existingInbox.get().markDuplicate(now);
            log.info("Duplicate audit event ignored eventId={} eventType={}", event.eventId(), event.eventType());
            return;
        }

        boolean unknownVersion = !SUPPORTED_SCHEMA_VERSION.equals(event.schemaVersion())
                || !PHASE_1_EVENTS.contains(event.eventType());
        String sanitizedPayload = json.canonicalJson(sanitizer.sanitize(event.payload()));
        try {
            inbox.saveAndFlush(new InboxEventEntity(
                    event.eventId(),
                    event.eventType(),
                    event.applicationId(),
                    payloadHash,
                    now
            ));
            auditEvents.save(new AuditEventEntity(
                    event.eventId(),
                    event.eventType(),
                    event.schemaVersion(),
                    event.occurredAt(),
                    event.producer(),
                    event.applicationId(),
                    event.caseId(),
                    event.evaluationRunId(),
                    event.correlationId(),
                    event.causationId(),
                    sanitizedPayload,
                    payloadHash,
                    unknownVersion,
                    now
            ));
        } catch (DataIntegrityViolationException duplicate) {
            inbox.findById(event.eventId()).ifPresent(existing -> existing.markDuplicate(now));
        }
    }
}
