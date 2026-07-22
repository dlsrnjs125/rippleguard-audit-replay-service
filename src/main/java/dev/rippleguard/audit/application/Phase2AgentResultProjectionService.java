package dev.rippleguard.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rippleguard.audit.domain.AuditQuarantineReason;
import dev.rippleguard.audit.infrastructure.persistence.AgentAttemptAuditEntity;
import dev.rippleguard.audit.infrastructure.persistence.AgentAttemptAuditRepository;
import dev.rippleguard.audit.infrastructure.persistence.AgentRunAuditEntity;
import dev.rippleguard.audit.infrastructure.persistence.AgentRunAuditRepository;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class Phase2AgentResultProjectionService {
    private static final Logger log = LoggerFactory.getLogger(Phase2AgentResultProjectionService.class);
    public static final String EVENT_TYPE = "governance.agent-result.validated.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    private static final String PRODUCER = "governance-service";
    private static final String SCHEMA = "events/governance.agent-result.validated.v1.0.0.schema.json";

    private final ContractSchemaValidator contracts;
    private final JsonSupport json;
    private final AuditEventRepository auditEvents;
    private final AgentRunAuditRepository agentRuns;
    private final AgentAttemptAuditRepository attempts;

    public Phase2AgentResultProjectionService(ContractSchemaValidator contracts,
                                              JsonSupport json,
                                              AuditEventRepository auditEvents,
                                              AgentRunAuditRepository agentRuns,
                                              AgentAttemptAuditRepository attempts) {
        this.contracts = contracts;
        this.json = json;
        this.auditEvents = auditEvents;
        this.agentRuns = agentRuns;
        this.attempts = attempts;
    }

    public boolean supports(EventEnvelope event) {
        return EVENT_TYPE.equals(event.eventType());
    }

    public AuditQuarantineReason rejectionReason(EventEnvelope event, JsonNode rawEnvelope) {
        return rejectionReason(event, rawEnvelope, true);
    }

    public AuditQuarantineReason rejectionReason(EventEnvelope event, JsonNode rawEnvelope, boolean requirePredecessor) {
        if (!SCHEMA_VERSION.equals(event.schemaVersion())) {
            return AuditQuarantineReason.UNSUPPORTED_SCHEMA_VERSION;
        }
        if (!PRODUCER.equals(event.producer())) {
            return AuditQuarantineReason.INVALID_PRODUCER;
        }
        try {
            contracts.validate(SCHEMA, rawEnvelope);
        } catch (ContractValidationException exception) {
            log.warn("Phase 2 validation event failed contract validation eventId={} reason={}",
                    event.eventId(), exception.getMessage());
            return AuditQuarantineReason.CONTRACT_VALIDATION_FAILED;
        }
        if (event.causationId() == null || event.causationId().equals(event.eventId())) {
            return AuditQuarantineReason.BROKEN_CAUSATION;
        }
        if (!event.correlationId().equals(event.applicationId().toString())) {
            return AuditQuarantineReason.BROKEN_CAUSATION;
        }
        JsonNode payload = event.payload();
        if (!event.caseId().equals(payload.path("decisionCaseId").asText())
                || !event.evaluationRunId().toString().equals(payload.path("evaluationRunId").asText())) {
            return AuditQuarantineReason.CONTRACT_VALIDATION_FAILED;
        }
        UUID agentRunId = UUID.fromString(payload.path("agentRunId").asText());
        int attemptId = payload.path("attemptId").asInt();
        if (event.causationId().equals(agentRunId)) {
            return AuditQuarantineReason.BROKEN_CAUSATION;
        }
        String expectedReference = "agent-result://" + event.caseId() + "/" + agentRunId + "/attempt-" + attemptId;
        if (!expectedReference.equals(payload.path("agentResultReference").asText())) {
            return AuditQuarantineReason.RESULT_REFERENCE_MISMATCH;
        }
        var existingRun = agentRuns.findById(agentRunId);
        if (existingRun.isPresent() && conflictsWith(existingRun.get(), payload, attemptId)) {
            return AuditQuarantineReason.DUPLICATE_AGENT_RUN_CONFLICT;
        }
        if (requirePredecessor && !auditEvents.existsById(event.causationId())) {
            return AuditQuarantineReason.BROKEN_CAUSATION;
        }
        return null;
    }

    public boolean isProjectionConflict(EventEnvelope event) {
        JsonNode payload = event.payload();
        UUID agentRunId = UUID.fromString(payload.path("agentRunId").asText());
        return agentRuns.findById(agentRunId)
                .map(existing -> conflictsWith(existing, payload, payload.path("attemptId").asInt()))
                .orElse(false);
    }

    public void project(EventEnvelope event) {
        JsonNode payload = event.payload();
        UUID agentRunId = UUID.fromString(payload.path("agentRunId").asText());
        int attemptId = payload.path("attemptId").asInt();
        String outcome = payload.path("validationOutcome").asText();
        String resultDigest = payload.path("agentResultDigest").asText();
        Instant validatedAt = OffsetDateTime.parse(payload.path("validatedAt").asText()).toInstant();
        String reasonCodes = json.canonicalJson(payload.path("validationReasonCodes"));

        if (agentRuns.findById(agentRunId).isPresent()) {
            AgentRunAuditEntity existing = agentRuns.findById(agentRunId).orElseThrow();
            if (existing.getAgentResultDigest().equals(resultDigest)
                    && existing.getValidationOutcome().equals(outcome)
                    && existing.getLatestAttemptId() == attemptId) {
                return;
            }
            throw new DataIntegrityViolationException("Conflicting agent run projection for " + agentRunId);
        }

        agentRuns.save(new AgentRunAuditEntity(
                agentRunId,
                payload.path("decisionCaseId").asText(),
                UUID.fromString(payload.path("evaluationRunId").asText()),
                outcome,
                reasonCodes,
                payload.path("agentResultReference").asText(),
                resultDigest,
                payload.path("validatedSchemaVersion").asText(),
                validatedAt,
                attemptId,
                event.occurredAt(),
                event.eventId(),
                event.schemaVersion()
        ));
        attempts.save(new AgentAttemptAuditEntity(
                agentRunId,
                attemptId,
                event.eventId(),
                outcome,
                resultDigest,
                validatedAt
        ));
    }

    private boolean conflictsWith(AgentRunAuditEntity existing, JsonNode payload, int attemptId) {
        return !existing.getAgentResultDigest().equals(payload.path("agentResultDigest").asText())
                || !existing.getValidationOutcome().equals(payload.path("validationOutcome").asText())
                || existing.getLatestAttemptId() != attemptId;
    }

}
