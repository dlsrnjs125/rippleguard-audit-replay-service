package dev.rippleguard.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rippleguard.audit.application.AuditIngestionService;
import dev.rippleguard.audit.application.EventEnvelope;
import dev.rippleguard.audit.domain.TraceCompleteness;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventRepository;
import dev.rippleguard.audit.infrastructure.persistence.InboxEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from audit_event");
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
        String caseId = "case-004";
        UUID runId = UUID.fromString("20000000-0000-4000-8000-000000000004");
        EventEnvelope submitted = event("loan.application.submitted.v1", applicationId, caseId, null, null,
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
                        "applicantReference", "applicant-ref"
                ))
        );

        ingestion.ingest(event);

        String sanitized = auditEvents.findById(event.eventId()).orElseThrow().getSanitizedPayload();
        assertThat(sanitized).contains("[REDACTED]");
        assertThat(sanitized).doesNotContain("raw document");
        assertThat(sanitized).doesNotContain("987654321");
        assertThat(sanitized).contains("applicant-ref");
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
                objectMapper.valueToTree(Map.of("applicationId", applicationId.toString()))
        );

        ingestion.ingest(event);

        assertThat(timeline("case-007").warnings()).contains("UNKNOWN_EVENT_VERSION");
    }

    private dev.rippleguard.audit.interfaces.rest.CaseTimelineResponse timeline(String caseId) {
        return new dev.rippleguard.audit.application.TimelineService(auditEvents).timeline(caseId);
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
}
