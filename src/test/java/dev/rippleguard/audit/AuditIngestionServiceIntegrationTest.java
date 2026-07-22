package dev.rippleguard.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rippleguard.audit.application.AuditIngestionService;
import dev.rippleguard.audit.application.EventEnvelope;
import dev.rippleguard.audit.application.TimelineService;
import dev.rippleguard.audit.domain.TraceCompleteness;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventRepository;
import dev.rippleguard.audit.infrastructure.persistence.InboxEventRepository;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "debug=false",
        "rippleguard.kafka.enabled=false",
        "management.health.kafka.enabled=false"
})
@AutoConfigureMockMvc
class AuditIngestionServiceIntegrationTest {
    @Autowired
    AuditIngestionService ingestion;

    @Autowired
    AuditEventRepository auditEvents;

    @Autowired
    InboxEventRepository inbox;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TimelineService timelineService;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from agent_attempt_audit");
        jdbc.update("delete from agent_run_audit");
        jdbc.update("delete from audit_event");
        jdbc.update("delete from audit_event_quarantine");
        jdbc.update("delete from inbox_event");
    }

    @Test
    void storesEventAndProvidesTimelineApi() throws Exception {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        EventEnvelope submitted = event("loan.application.submitted.v1", applicationId, applicationId.toString(), null, null,
                Instant.parse("2026-01-01T00:00:00Z"));

        ingestion.ingest(submitted);

        assertThat(auditEvents.count()).isEqualTo(1);
        mvc.perform(get("/api/v1/cases/{caseId}/timeline", applicationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(applicationId.toString()))
                .andExpect(jsonPath("$.events[0].eventType").value("loan.application.submitted.v1"));
    }

    @Test
    void duplicateEventIsNotStoredTwiceAndInboxRecordsDuplicate() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000002");
        EventEnvelope submitted = event("loan.application.submitted.v1", applicationId, applicationId.toString(), null, null,
                Instant.parse("2026-01-01T00:00:00Z"));

        ingestion.ingest(submitted);
        ingestion.ingest(submitted);

        assertThat(auditEvents.count()).isEqualTo(1);
        assertThat(inbox.findById(submitted.eventId()).orElseThrow().getDuplicateCount()).isEqualTo(1);
    }

    @Test
    void timelineSortsByOccurredAtAndMarksLateEventWhenIngestionOrderWasReversed() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000003");
        EventEnvelope later = event("governance.review.started.v1", applicationId, "case-003", null, UUID.randomUUID(),
                Instant.parse("2026-01-01T00:01:00Z"));
        EventEnvelope earlier = event("loan.application.submitted.v1", applicationId, "case-003", null, null,
                Instant.parse("2026-01-01T00:00:00Z"));

        ingestion.ingest(later);
        ingestion.ingest(earlier);
        var rows = auditEvents.findByCaseIdOrderByOccurredAtAscIngestedAtAsc("case-003");

        assertThat(rows).extracting("eventType")
                .containsExactly("loan.application.submitted.v1", "governance.review.started.v1");
        assertThat(timeline("case-003").warnings()).contains("LATE_EVENT_PENDING", "INVALID_REFERENCE");
        assertThat(timeline("case-003").events().get(0).status().name()).isEqualTo("LATE");
    }

    @Test
    void completeCausationChainReturnsCompleteTrace() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000004");
        String caseId = "case-" + applicationId;
        UUID runId = UUID.fromString("20000000-0000-4000-8000-000000000004");
        EventEnvelope submitted = event("loan.application.submitted.v1", applicationId, applicationId.toString(), null, null,
                Instant.parse("2026-01-01T00:00:00Z"));
        EventEnvelope reviewStarted = event("governance.review.started.v1", applicationId, caseId, null, submitted.eventId(),
                Instant.parse("2026-01-01T00:01:00Z"));
        EventEnvelope requested = event("agent.evaluation.requested.v1", applicationId, caseId, runId, reviewStarted.eventId(),
                Instant.parse("2026-01-01T00:02:00Z"));
        EventEnvelope completed = event("agent.evaluation.completed.v1", applicationId, caseId, runId, requested.eventId(),
                Instant.parse("2026-01-01T00:03:00Z"));
        EventEnvelope commanded = event("loan.decision.commanded.v1", applicationId, caseId, runId, completed.eventId(),
                Instant.parse("2026-01-01T00:04:00Z"));
        EventEnvelope finalized = event("loan.decision.finalized.v1", applicationId, caseId, runId, commanded.eventId(),
                Instant.parse("2026-01-01T00:05:00Z"));

        for (EventEnvelope event : new EventEnvelope[]{submitted, reviewStarted, requested, completed, commanded, finalized}) {
            ingestion.ingest(event);
        }

        var timeline = timeline(caseId);
        assertThat(timeline.traceCompleteness()).isEqualTo(TraceCompleteness.COMPLETE);
        assertThat(timeline.warnings()).isEmpty();
        assertThat(timeline.events()).hasSize(6);
        assertThat(timeline.events()).extracting("eventType")
                .containsExactly(
                        "loan.application.submitted.v1",
                        "governance.review.started.v1",
                        "agent.evaluation.requested.v1",
                        "agent.evaluation.completed.v1",
                        "loan.decision.commanded.v1",
                        "loan.decision.finalized.v1"
                );
        assertThat(timeline.events()).allSatisfy(event -> assertThat(event.caseId()).isEqualTo(caseId));
        assertThat(timeline.caseId()).isEqualTo(caseId);
        assertCaseTimelineContractSchemaAndSemantics(timeline);
    }

    @Test
    void actualApiResponseNormalizesEventCaseIdsAndSatisfiesCaseTimelineContract() throws Exception {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000014");
        String caseId = "case-" + applicationId;
        UUID runId = UUID.fromString("20000000-0000-4000-8000-000000000014");
        EventEnvelope submitted = event("loan.application.submitted.v1", applicationId, applicationId.toString(), null, null,
                Instant.parse("2026-01-01T00:00:00Z"));
        EventEnvelope reviewStarted = event("governance.review.started.v1", applicationId, caseId, null, submitted.eventId(),
                Instant.parse("2026-01-01T00:01:00Z"));
        EventEnvelope requested = event("agent.evaluation.requested.v1", applicationId, caseId, runId, reviewStarted.eventId(),
                Instant.parse("2026-01-01T00:02:00Z"));
        EventEnvelope completed = event("agent.evaluation.completed.v1", applicationId, caseId, runId, requested.eventId(),
                Instant.parse("2026-01-01T00:03:00Z"));
        EventEnvelope commanded = event("loan.decision.commanded.v1", applicationId, caseId, runId, completed.eventId(),
                Instant.parse("2026-01-01T00:04:00Z"));
        EventEnvelope finalized = event("loan.decision.finalized.v1", applicationId, caseId, runId, commanded.eventId(),
                Instant.parse("2026-01-01T00:05:00Z"));

        for (EventEnvelope event : new EventEnvelope[]{submitted, reviewStarted, requested, completed, commanded, finalized}) {
            ingestion.ingest(event);
        }

        MvcResult result = mvc.perform(get("/api/v1/cases/{caseId}/timeline", caseId))
                .andExpect(status().isOk())
                .andReturn();
        var response = objectMapper.readValue(result.getResponse().getContentAsString(),
                dev.rippleguard.audit.interfaces.rest.CaseTimelineResponse.class);

        assertThat(response.events()).hasSize(6);
        assertThat(response.events()).allSatisfy(event -> assertThat(event.caseId()).isEqualTo(caseId));
        assertCaseTimelineContractSchemaAndSemantics(response);
    }

    @Test
    void missingPredecessorCreatesWarningAndPartialTrace() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000005");
        EventEnvelope completed = event("agent.evaluation.completed.v1", applicationId, "case-005", UUID.randomUUID(),
                UUID.randomUUID(), Instant.parse("2026-01-01T00:03:00Z"));

        ingestion.ingest(completed);

        var timeline = timeline("case-005");
        assertThat(timeline.traceCompleteness()).isEqualTo(TraceCompleteness.PARTIAL);
        assertThat(timeline.warnings()).contains("EVENT_GAP_DETECTED", "INVALID_REFERENCE");
        assertThat(timeline.events().get(0).status().name()).isEqualTo("INVALID_REFERENCE");
    }

    @Test
    void sanitizesSensitivePayloadFields() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000006");
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "loan.application.submitted.v1",
                "1.1.0",
                Instant.parse("2026-01-01T00:00:00Z"),
                "loan-service",
                applicationId,
                "case-006",
                null,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of(
                        "applicationId", applicationId.toString(),
                        "financialSnapshot", Map.of("income", 987654321),
                        "documentText", "raw document",
                        "applicantId", "synthetic:applicant-ref"
                ))
        );

        ingestion.ingest(event);

        String sanitized = auditEvents.findById(event.eventId()).orElseThrow().getSanitizedPayload();
        assertThat(sanitized).contains("synthetic:applicant-ref");
        assertThat(sanitized).doesNotContain("raw document");
        assertThat(sanitized).doesNotContain("987654321");
        assertThat(sanitized).doesNotContain("financialSnapshot");
        assertThat(sanitized).doesNotContain("documentText");
    }

    @Test
    void invalidApplicantIdReferenceIsRedactedForSupportedSubmittedEvent() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000016");
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "loan.application.submitted.v1",
                "1.1.0",
                Instant.parse("2026-01-01T00:00:00Z"),
                "loan-service",
                applicationId,
                "case-016",
                null,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of(
                        "applicationId", applicationId.toString(),
                        "applicantId", "홍길동-900101-1234567",
                        "inputSnapshotVersion", "snapshot-v1",
                        "submittedAt", "2026-01-01T00:00:00Z",
                        "submissionChannel", "PARTNER_API"
                ))
        );

        ingestion.ingest(event);

        String sanitized = auditEvents.findById(event.eventId()).orElseThrow().getSanitizedPayload();
        assertThat(sanitized).contains("[REDACTED_INVALID_REFERENCE]");
        assertThat(sanitized).doesNotContain("홍길동");
        assertThat(sanitized).doesNotContain("900101");
    }

    @Test
    void invalidEvidenceReferenceIsRedactedForSupportedCompletedEvent() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000017");
        UUID runId = UUID.fromString("20000000-0000-4000-8000-000000000017");
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "agent.evaluation.completed.v1",
                "1.1.0",
                Instant.parse("2026-01-01T00:00:00Z"),
                "governance-service",
                applicationId,
                "case-017",
                runId,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of(
                        "evaluationRunId", runId.toString(),
                        "decisionCaseId", "case-017",
                        "evaluationMode", "MOCK",
                        "evaluatorId", "mock-evaluator",
                        "decisionEnvelope", Map.of(
                                "decisionId", UUID.randomUUID().toString(),
                                "evaluationRunId", runId.toString(),
                                "decisionCaseId", "case-017",
                                "evaluatorId", "mock-evaluator",
                                "proposal", "PROPOSE_APPROVE",
                                "status", "PROPOSED",
                                "usedEvidenceRefs", List.of("snapshot://safe/ref", "raw-bank-account-1234")
                        )
                ))
        );

        ingestion.ingest(event);

        String sanitized = auditEvents.findById(event.eventId()).orElseThrow().getSanitizedPayload();
        assertThat(sanitized).contains("snapshot://safe/ref");
        assertThat(sanitized).contains("[REDACTED_INVALID_REFERENCE]");
        assertThat(sanitized).doesNotContain("raw-bank-account-1234");
    }

    @Test
    void unknownEventVersionIsRecordedWithWarning() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000007");
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "loan.application.submitted.v1",
                "9.9.9",
                Instant.parse("2026-01-01T00:00:00Z"),
                "loan-service",
                applicationId,
                "case-007",
                null,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of(
                        "applicationId", applicationId.toString(),
                        "incomeHistory", "private-income",
                        "requestedAmount", "30000000.00",
                        "apiKey", "secret-key"
                ))
        );

        ingestion.ingest(event);

        assertThat(timeline("case-007").warnings()).contains("EVENT_GAP_DETECTED");
        String sanitized = auditEvents.findById(event.eventId()).orElseThrow().getSanitizedPayload();
        assertThat(sanitized).contains("\"redacted\":true");
        assertThat(sanitized).contains("UNSUPPORTED_SCHEMA_VERSION");
        assertThat(sanitized).contains("incomeHistory", "requestedAmount", "apiKey");
        assertThat(sanitized).doesNotContain("private-income", "30000000.00", "secret-key");
    }

    @Test
    void unsupportedEventTypeUsesUnsupportedEventTypeReason() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000018");
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "loan.private.snapshot.v1",
                "1.1.0",
                Instant.parse("2026-01-01T00:00:00Z"),
                "loan-service",
                applicationId,
                "case-018",
                null,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of("requestedAmount", "30000000.00"))
        );

        ingestion.ingest(event);

        String sanitized = auditEvents.findById(event.eventId()).orElseThrow().getSanitizedPayload();
        assertThat(sanitized).contains("UNSUPPORTED_EVENT_TYPE");
        assertThat(sanitized).doesNotContain("30000000.00");
    }

    @Test
    void nonDuplicateIntegrityViolationIsRethrownAndDoesNotDisappear() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000019");
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "loan.application.submitted.v1",
                "1.1.0",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                applicationId,
                "case-019",
                null,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of("applicationId", applicationId.toString()))
        );

        assertThatThrownBy(() -> ingestion.ingest(event))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(auditEvents.count()).isZero();
        assertThat(inbox.count()).isZero();
    }

    @Test
    void malformedRawEventIsQuarantinedWithoutStorage() {
        boolean ingested = ingestion.tryIngestRaw("{\"eventId\":\"not-a-uuid\",\"payload\":{\"ssn\":\"900101-1234567\"}");

        assertThat(ingested).isFalse();
        assertThat(auditEvents.count()).isZero();
        assertThat(inbox.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_event_quarantine", Long.class)).isEqualTo(1);
    }

    @Test
    void phase2ValidatedEventCreatesAgentRunAttemptTimelineAndApi() throws Exception {
        boolean ingested = ingestion.tryIngestRaw(contractFixture("examples/valid/events/v1.0.0/governance.agent-result.validated.v1.json"));

        assertThat(ingested).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from agent_run_audit", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from agent_attempt_audit", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select validation_outcome from agent_run_audit", String.class))
                .isEqualTo("VALIDATED");
        assertThat(jdbc.queryForObject("select attempt_count from agent_run_audit", Integer.class)).isEqualTo(1);
        var timeline = timeline("case-2001");
        assertThat(timeline.events()).extracting("eventType")
                .contains("governance.agent-result.validated.v1");
        assertThat(timeline.warnings()).doesNotContain("INVALID_REFERENCE");

        mvc.perform(get("/api/v1/agent-runs/{agentRunId}", "60000000-0000-4000-8000-000000002001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationOutcome").value("VALIDATED"))
                .andExpect(jsonPath("$.validationReasonCodes[0]").value("SCHEMA_VALID"))
                .andExpect(jsonPath("$.attempts[0].attemptId").value(1))
                .andExpect(jsonPath("$.modelVersion").doesNotExist());

        mvc.perform(get("/api/v1/evaluation-runs/{evaluationRunId}/agent-runs",
                        "30000000-0000-4000-8000-000000002001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentRunId").value("60000000-0000-4000-8000-000000002001"));
    }

    @Test
    void phase2RejectedEventKeepsOutcomeDistinctFromAuditIngestionStatus() {
        boolean ingested = ingestion.tryIngestRaw(contractFixture("examples/valid/events/v1.0.0/governance.agent-result.validated.v1--rejected.json"));

        assertThat(ingested).isTrue();
        assertThat(jdbc.queryForObject("select validation_outcome from agent_run_audit", String.class))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select ingestion_status from agent_run_audit", String.class))
                .isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("select latest_attempt_id from agent_run_audit", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select attempt_count from agent_run_audit", Integer.class)).isEqualTo(1);
        assertThat(timeline("case-2001").events()).extracting("summary")
                .contains("Agent result rejected by Governance");
    }

    @Test
    void phase2InvalidProducerIsQuarantinedWithoutProjection() {
        boolean ingested = ingestion.tryIngestRaw(contractFixture(
                "examples/invalid/events/v1.0.0/governance.agent-result.validated.v1--agent-runtime-producer.json"));

        assertThat(ingested).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from agent_run_audit", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select reason_code from audit_event_quarantine", String.class))
                .isEqualTo("INVALID_PRODUCER");
    }

    @Test
    void phase2BrokenCausationIsQuarantinedWithoutProjection() {
        boolean ingested = ingestion.tryIngestRaw(contractFixture(
                "examples/invalid/events/v1.0.0/governance.agent-result.validated.v1--broken-causation.json"));

        assertThat(ingested).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from agent_run_audit", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select reason_code from audit_event_quarantine", String.class))
                .isEqualTo("BROKEN_CAUSATION");
    }

    @Test
    void phase2ConflictingAgentRunIsQuarantinedWithoutOverwritingProjection() {
        ingestion.tryIngestRaw(contractFixture("examples/valid/events/v1.0.0/governance.agent-result.validated.v1.json"));

        boolean ingested = ingestion.tryIngestRaw(contractFixture(
                "examples/valid/events/v1.0.0/governance.agent-result.validated.v1--rejected.json"));

        assertThat(ingested).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from agent_run_audit", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select validation_outcome from agent_run_audit", String.class))
                .isEqualTo("VALIDATED");
        assertThat(jdbc.queryForObject("select reason_code from audit_event_quarantine", String.class))
                .isEqualTo("DUPLICATE_AGENT_RUN_CONFLICT");
        assertThat(auditEvents.count()).isEqualTo(1);
    }

    @Test
    void phase2TimelineDoesNotInferValidatedWhenProjectionIsMissing() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000002001");
        UUID eventId = UUID.fromString("10000000-0000-4000-8000-000000002777");
        UUID agentRunId = UUID.fromString("60000000-0000-4000-8000-000000002001");
        auditEvents.save(new AuditEventEntity(
                eventId,
                "governance.agent-result.validated.v1",
                "1.0.0",
                Instant.parse("2026-07-21T01:10:04Z"),
                "governance-service",
                applicationId,
                "case-2001",
                UUID.fromString("30000000-0000-4000-8000-000000002001"),
                applicationId.toString(),
                agentRunId,
                "{\"validationOutcome\":\"VALIDATED\"}",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                false,
                Instant.parse("2026-07-21T01:10:05Z")
        ));

        assertThat(timeline("case-2001").events()).extracting("summary")
                .contains("Agent result projection unavailable");
    }

    @Test
    void sameEventIdDifferentPayloadIsConflictQuarantined() {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000020");
        EventEnvelope first = event("loan.application.submitted.v1", applicationId, "case-020", null, null,
                Instant.parse("2026-01-01T00:00:00Z"));
        EventEnvelope conflicting = new EventEnvelope(
                first.eventId(),
                first.eventType(),
                first.schemaVersion(),
                first.occurredAt(),
                first.producer(),
                first.applicationId(),
                first.caseId(),
                first.evaluationRunId(),
                first.correlationId(),
                first.causationId(),
                objectMapper.valueToTree(Map.of("applicationId", applicationId.toString(), "status", "different"))
        );

        ingestion.ingest(first);
        ingestion.ingest(conflicting);

        assertThat(auditEvents.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reason_code from audit_event_quarantine", String.class))
                .isEqualTo("CONFLICTING_EVENT_PAYLOAD");
    }

    private dev.rippleguard.audit.interfaces.rest.CaseTimelineResponse timeline(String caseId) {
        return timelineService.timeline(caseId);
    }

    private void assertCaseTimelineContractSchemaAndSemantics(
            dev.rippleguard.audit.interfaces.rest.CaseTimelineResponse response) {
        assertThat(response.schemaVersion()).isEqualTo("1.0.0");
        assertThat(response.caseId()).isNotBlank();
        assertThat(response.applicationId()).isNotNull();
        assertThat(response.events()).isNotNull();
        assertThat(response.traceCompleteness()).isNotNull();
        assertThat(response.warnings()).allSatisfy(warning ->
                assertThat(warning).isIn("EVENT_GAP_DETECTED", "RETENTION_LIMIT", "LATE_EVENT_PENDING", "INVALID_REFERENCE"));

        Set<UUID> identifiers = new HashSet<>();
        Set<UUID> seen = new HashSet<>();
        Instant previous = null;
        for (var event : response.events()) {
            assertThat(identifiers.add(event.eventId())).isTrue();
            assertThat(event.eventType()).isNotBlank();
            assertThat(event.caseId()).isEqualTo(response.caseId());
            assertThat(event.occurredAt()).isNotNull();
            assertThat(event.producer()).isNotBlank();
            assertThat(event.correlationId()).isEqualTo(response.applicationId().toString());
            assertThat(event.status().name()).isIn("RECORDED", "LATE", "DUPLICATE", "INVALID_REFERENCE");
            assertThat(event.summary()).isNotBlank();
            if (previous != null) {
                assertThat(event.occurredAt()).isAfterOrEqualTo(previous);
            }
            previous = event.occurredAt();
            if (event.causationId() != null) {
                assertThat(seen).contains(event.causationId());
            }
            seen.add(event.eventId());
        }
        if (response.traceCompleteness() == TraceCompleteness.COMPLETE) {
            assertThat(response.events()).noneMatch(event -> event.status().name().equals("INVALID_REFERENCE"));
        }
        if (response.traceCompleteness() == TraceCompleteness.PARTIAL
                || response.traceCompleteness() == TraceCompleteness.UNKNOWN) {
            assertThat(response.warnings()).isNotEmpty();
        }
    }

    private EventEnvelope event(String eventType, UUID applicationId, String caseId, UUID evaluationRunId,
                                UUID causationId, Instant occurredAt) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                "1.1.0",
                occurredAt,
                producer(eventType),
                applicationId,
                caseId,
                evaluationRunId,
                applicationId.toString(),
                causationId,
                objectMapper.valueToTree(Map.of(
                        "applicationId", applicationId.toString(),
                        "caseId", caseId,
                        "status", eventType
                ))
        );
    }

    private String producer(String eventType) {
        return eventType.startsWith("governance.") || eventType.startsWith("agent.")
                || eventType.equals("loan.decision.commanded.v1") ? "governance-service" : "loan-service";
    }

    private String contractFixture(String relativePath) {
        String resourcePath = "/contracts/" + relativePath;
        try (var stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing contract fixture: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Failed to read contract fixture: " + resourcePath, exception);
        }
    }
}
