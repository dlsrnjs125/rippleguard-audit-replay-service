package dev.rippleguard.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rippleguard.audit.domain.AuditQuarantineReason;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventQuarantineEntity;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventQuarantineRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final AuditEventQuarantineRepository quarantine;
    private final InboxEventRepository inbox;
    private final Phase2AgentResultProjectionService phase2Projection;
    private final JsonSupport json;
    private final PayloadSanitizer sanitizer;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public AuditIngestionService(AuditEventRepository auditEvents,
                                 AuditEventQuarantineRepository quarantine,
                                 InboxEventRepository inbox,
                                 Phase2AgentResultProjectionService phase2Projection,
                                 JsonSupport json,
                                 PayloadSanitizer sanitizer,
                                 Clock clock,
                                 TransactionTemplate transactions) {
        this.auditEvents = auditEvents;
        this.quarantine = quarantine;
        this.inbox = inbox;
        this.phase2Projection = phase2Projection;
        this.json = json;
        this.sanitizer = sanitizer;
        this.clock = clock;
        this.transactions = transactions;
    }

    public void ingestRaw(String rawMessage) {
        JsonNode rawEnvelope = json.jsonNode(rawMessage);
        ingest(json.eventEnvelope(rawMessage), rawEnvelope);
    }

    public boolean tryIngestRaw(String rawMessage) {
        try {
            ingestRaw(rawMessage);
            return true;
        } catch (IllegalArgumentException exception) {
            quarantineMalformed(rawMessage, exception.getMessage());
            return false;
        }
    }

    public void ingest(EventEnvelope event) {
        ingest(event, json.eventEnvelopeNode(event));
    }

    public void ingest(EventEnvelope event, JsonNode rawEnvelope) {
        try {
            transactions.executeWithoutResult(status -> ingestInTransaction(event, rawEnvelope));
        } catch (DataIntegrityViolationException duplicate) {
            if (!recordDuplicateInNewTransaction(event)) {
                throw duplicate;
            }
        }
    }

    private void ingestInTransaction(EventEnvelope event, JsonNode rawEnvelope) {
        Instant now = clock.instant();
        String payloadHash = json.sha256(rawEnvelope);
        var existingInbox = inbox.findById(event.eventId());
        if (existingInbox.isPresent()) {
            if (existingInbox.get().getPayloadHash().equals(payloadHash)) {
                existingInbox.get().markDuplicate(now);
                log.info("Duplicate audit event ignored eventId={} eventType={}", event.eventId(), event.eventType());
                return;
            }
            existingInbox.get().markConflict(now);
            quarantine(event, AuditQuarantineReason.CONFLICTING_EVENT_PAYLOAD, payloadHash, now, false);
            log.warn("Conflicting audit event quarantined eventId={} eventType={}", event.eventId(), event.eventType());
            return;
        }

        if (phase2Projection.supports(event)) {
            AuditQuarantineReason reason = phase2Projection.rejectionReason(event, rawEnvelope);
            if (reason != null) {
                quarantine(event, reason, payloadHash, now, false);
                return;
            }
        }

        String unsupportedReason = unsupportedReason(event);
        boolean unknownVersion = unsupportedReason != null;
        String sanitizedPayload = json.canonicalJson(sanitizer.sanitize(event, payloadHash, unsupportedReason));
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
        if (phase2Projection.supports(event)) {
            try {
                phase2Projection.project(event);
            } catch (DataIntegrityViolationException conflict) {
                quarantine(event, AuditQuarantineReason.DUPLICATE_AGENT_RUN_CONFLICT, payloadHash, now, false);
                throw conflict;
            }
        }
    }

    private boolean recordDuplicateInNewTransaction(EventEnvelope event) {
        return Boolean.TRUE.equals(transactions.execute(status -> inbox.findById(event.eventId())
                .map(existing -> {
                    existing.markDuplicate(clock.instant());
                    log.info("Recovered concurrent duplicate audit event eventId={} eventType={}",
                            event.eventId(), event.eventType());
                    return true;
                })
                .orElse(false)));
    }

    private String unsupportedReason(EventEnvelope event) {
        if (!PHASE_1_EVENTS.contains(event.eventType())) {
            return "UNSUPPORTED_EVENT_TYPE";
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(event.schemaVersion())) {
            return "UNSUPPORTED_SCHEMA_VERSION";
        }
        return null;
    }

    private void quarantineMalformed(String rawMessage, String reason) {
        transactions.executeWithoutResult(status -> quarantine.save(new AuditEventQuarantineEntity(
                java.util.UUID.randomUUID(),
                null,
                null,
                null,
                null,
                clock.instant(),
                AuditQuarantineReason.MALFORMED_EVENT,
                json.sha256(rawMessage),
                null,
                null,
                false
        )));
        log.warn("Malformed audit event quarantined payloadHash={} reason={}", json.sha256(rawMessage), reason);
    }

    private void quarantine(EventEnvelope event, AuditQuarantineReason reason, String payloadHash,
                            Instant receivedAt, boolean retryEligible) {
        quarantine.save(new AuditEventQuarantineEntity(
                java.util.UUID.randomUUID(),
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                event.producer(),
                receivedAt,
                reason,
                payloadHash,
                event.correlationId(),
                event.causationId(),
                retryEligible
        ));
        log.warn("Audit event quarantined eventId={} eventType={} reason={} retryEligible={}",
                event.eventId(), event.eventType(), reason, retryEligible);
    }
}
