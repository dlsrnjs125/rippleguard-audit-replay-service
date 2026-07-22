package dev.rippleguard.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rippleguard.audit.config.Phase2PendingCausationProperties;
import dev.rippleguard.audit.domain.AuditQuarantineReason;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventQuarantineEntity;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventQuarantineRepository;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventRepository;
import dev.rippleguard.audit.infrastructure.persistence.InboxEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.InboxEventRepository;
import dev.rippleguard.audit.infrastructure.persistence.PendingCausationEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.PendingCausationEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final PendingCausationEventRepository pendingCausation;
    private final Phase2AgentResultProjectionService phase2Projection;
    private final JsonSupport json;
    private final PayloadSanitizer sanitizer;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final Duration pendingTtl;
    private final Duration pendingRetryDelay;
    private final int maxPendingAttempts;
    private final int pendingBatchSize;

    public AuditIngestionService(AuditEventRepository auditEvents,
                                 AuditEventQuarantineRepository quarantine,
                                 InboxEventRepository inbox,
                                 PendingCausationEventRepository pendingCausation,
                                 Phase2AgentResultProjectionService phase2Projection,
                                 JsonSupport json,
                                 PayloadSanitizer sanitizer,
                                 Clock clock,
                                 TransactionTemplate transactions,
                                 Phase2PendingCausationProperties pendingProperties) {
        this.auditEvents = auditEvents;
        this.quarantine = quarantine;
        this.inbox = inbox;
        this.pendingCausation = pendingCausation;
        this.phase2Projection = phase2Projection;
        this.json = json;
        this.sanitizer = sanitizer;
        this.clock = clock;
        this.transactions = transactions;
        this.pendingTtl = pendingProperties.ttl();
        this.pendingRetryDelay = pendingProperties.retryDelay();
        this.maxPendingAttempts = pendingProperties.maxAttempts();
        this.pendingBatchSize = pendingProperties.batchSize();
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
        String payloadHash = json.sha256(rawEnvelope);
        try {
            transactions.executeWithoutResult(status -> ingestInTransaction(event, rawEnvelope));
        } catch (DataIntegrityViolationException exception) {
            DuplicateRecoveryResult recovery = recoverDuplicateInNewTransaction(event, payloadHash);
            switch (recovery) {
                case DUPLICATE, CONFLICT -> {
                    return;
                }
                case NOT_FOUND -> {
                    if (phase2Projection.supports(event) && phase2Projection.isProjectionConflict(event)) {
                        quarantineInNewTransaction(event, AuditQuarantineReason.DUPLICATE_AGENT_RUN_CONFLICT,
                                payloadHash, false);
                        return;
                    }
                    throw exception;
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${rippleguard.phase2.pending-causation.reconcile-delay-ms:30000}")
    public void reconcilePendingCausation() {
        transactions.executeWithoutResult(status -> reconcilePendingCausation(clock.instant()));
    }

    private void ingestInTransaction(EventEnvelope event, JsonNode rawEnvelope) {
        Instant now = clock.instant();
        String payloadHash = json.sha256(rawEnvelope);
        expirePendingCausation(now);
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
            AuditQuarantineReason reason = phase2Projection.rejectionReason(event, rawEnvelope, false);
            if (reason != null) {
                quarantine(event, reason, payloadHash, now, false);
                return;
            }
            if (!auditEvents.existsById(event.causationId())) {
                savePendingCausation(event, rawEnvelope, payloadHash, now);
                return;
            }
        }

        persistAcceptedEvent(event, rawEnvelope, payloadHash, now);
        resolvePendingCausedBy(event.eventId(), now);
    }

    private void persistAcceptedEvent(EventEnvelope event, JsonNode rawEnvelope, String payloadHash, Instant now) {
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
            phase2Projection.project(event);
        }
    }

    private void savePendingCausation(EventEnvelope event, JsonNode rawEnvelope, String payloadHash, Instant now) {
        var existing = pendingCausation.findById(event.eventId());
        if (existing.isPresent()) {
            if (existing.get().getRawPayloadHash().equals(payloadHash)) {
                log.info("Duplicate pending causation event ignored eventId={} causationId={}",
                        event.eventId(), event.causationId());
                return;
            }
            quarantine(event, AuditQuarantineReason.CONFLICTING_EVENT_PAYLOAD, payloadHash, now, false);
            return;
        }
        pendingCausation.save(new PendingCausationEventEntity(
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                event.producer(),
                event.applicationId(),
                event.caseId(),
                event.evaluationRunId(),
                event.correlationId(),
                event.causationId(),
                json.canonicalJson(rawEnvelope),
                payloadHash,
                now,
                now.plus(pendingRetryDelay),
                0,
                now.plus(pendingTtl),
                PendingCausationEventEntity.PENDING
        ));
        log.warn("Audit event pending causation eventId={} eventType={} causationId={} expiresAt={}",
                event.eventId(), event.eventType(), event.causationId(), now.plus(pendingTtl));
    }

    private void resolvePendingCausedBy(UUID predecessorEventId, Instant now) {
        for (PendingCausationEventEntity pending : pendingCausation.claimByCausationId(predecessorEventId)) {
            resolvePending(pending, now);
        }
    }

    private void reconcilePendingCausation(Instant now) {
        expirePendingCausation(now);
        for (PendingCausationEventEntity pending : pendingCausation.claimDueForReconciliation(now, pendingBatchSize)) {
            if (auditEvents.existsById(pending.getCausationId())) {
                resolvePending(pending, now);
            } else {
                pending.markAttempt(now.plus(pendingRetryDelay));
            }
        }
    }

    private void expirePendingCausation(Instant now) {
        for (PendingCausationEventEntity pending : pendingCausation.claimExpired(now, pendingBatchSize)) {
            expirePending(pending, now);
        }
        for (PendingCausationEventEntity pending :
                pendingCausation.claimAttemptExhausted(now, maxPendingAttempts, pendingBatchSize)) {
            expirePending(pending, now);
        }
    }

    private void resolvePending(PendingCausationEventEntity pending, Instant now) {
        if (auditEvents.existsById(pending.getEventId())) {
            pending.markResolved();
            return;
        }
        pending.markAttempt(now.plus(pendingRetryDelay));
        EventEnvelope pendingEvent = json.eventEnvelope(pending.getRawPayload());
        JsonNode rawEnvelope = json.jsonNode(pending.getRawPayload());
        AuditQuarantineReason reason = phase2Projection.rejectionReason(pendingEvent, rawEnvelope, true);
        if (reason != null) {
            pending.markExpired();
            quarantine(pendingEvent, reason, pending.getRawPayloadHash(), now, false);
            return;
        }
        persistAcceptedEvent(pendingEvent, rawEnvelope, pending.getRawPayloadHash(), now);
        pending.markResolved();
        log.info("Resolved pending causation eventId={} causationId={}",
                pending.getEventId(), pending.getCausationId());
    }

    private void expirePending(PendingCausationEventEntity pending, Instant now) {
        pending.markExpired();
        EventEnvelope pendingEvent = json.eventEnvelope(pending.getRawPayload());
        quarantine(pendingEvent, AuditQuarantineReason.BROKEN_CAUSATION, pending.getRawPayloadHash(), now, false);
    }

    private DuplicateRecoveryResult recoverDuplicateInNewTransaction(EventEnvelope event, String incomingPayloadHash) {
        DuplicateRecoveryResult result = transactions.execute(status -> inbox.findById(event.eventId())
                .map(existing -> {
                    if (!existing.getPayloadHash().equals(incomingPayloadHash)) {
                        existing.markConflict(clock.instant());
                        quarantine(event, AuditQuarantineReason.CONFLICTING_EVENT_PAYLOAD,
                                incomingPayloadHash, clock.instant(), false);
                        log.warn("Recovered concurrent conflicting audit event eventId={} eventType={}",
                                event.eventId(), event.eventType());
                        return DuplicateRecoveryResult.CONFLICT;
                    }
                    existing.markDuplicate(clock.instant());
                    log.info("Recovered concurrent duplicate audit event eventId={} eventType={}",
                            event.eventId(), event.eventType());
                    return DuplicateRecoveryResult.DUPLICATE;
                })
                .orElse(DuplicateRecoveryResult.NOT_FOUND));
        return result == null ? DuplicateRecoveryResult.NOT_FOUND : result;
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

    private void quarantineInNewTransaction(EventEnvelope event, AuditQuarantineReason reason, String payloadHash,
                                            boolean retryEligible) {
        transactions.executeWithoutResult(status ->
                quarantine(event, reason, payloadHash, clock.instant(), retryEligible));
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

    private enum DuplicateRecoveryResult {
        DUPLICATE,
        CONFLICT,
        NOT_FOUND
    }
}
