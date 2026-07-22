package dev.rippleguard.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rippleguard.audit.application.AuditIngestionService;
import dev.rippleguard.audit.application.EventEnvelope;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "debug=false",
        "rippleguard.kafka.enabled=false",
        "management.health.kafka.enabled=false"
})
class PostgresMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rippleguard_audit")
            .withUsername("rippleguard_audit")
            .withPassword("rippleguard_audit");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    DataSource dataSource;

    @Autowired
    AuditIngestionService ingestion;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from agent_attempt_audit");
        jdbc.update("delete from agent_run_audit");
        jdbc.update("delete from audit_event_quarantine");
        jdbc.update("delete from audit_event");
        jdbc.update("delete from inbox_event");
    }

    @Test
    void appliesFlywayMigrationOnPostgreSql() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("select count(*) from audit_event")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void concurrentDuplicateEventCreatesSingleAuditEventAndIncrementsInboxDuplicateCount() throws Exception {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000091");
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "loan.application.submitted.v1",
                "1.1.0",
                Instant.parse("2026-01-01T00:00:00Z"),
                "loan-service",
                applicationId,
                applicationId.toString(),
                null,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of(
                        "applicationId", applicationId.toString(),
                        "applicantId", "synthetic:applicant-ref",
                        "inputSnapshotVersion", "snapshot-v1",
                        "submittedAt", "2026-01-01T00:00:00Z",
                        "submissionChannel", "PARTNER_API"
                ))
        );

        var results = runConcurrently(
                () -> {
                    ingestion.ingest(event);
                    return null;
                },
                () -> {
                    ingestion.ingest(event);
                    return null;
                }
        );

        assertThat(results).filteredOn(Throwable.class::isInstance).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inbox_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select duplicate_count from inbox_event where event_id = ?",
                Integer.class, event.eventId())).isEqualTo(1);
    }

    @Test
    void concurrentSameEventIdDifferentPayloadCreatesConflictQuarantine() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000000092");
        EventEnvelope first = submittedEvent(eventId, applicationId, "synthetic:applicant-ref-a");
        EventEnvelope second = submittedEvent(eventId, applicationId, "synthetic:applicant-ref-b");

        var results = runConcurrently(
                () -> {
                    ingestion.ingest(first);
                    return null;
                },
                () -> {
                    ingestion.ingest(second);
                    return null;
                }
        );

        assertThat(results).filteredOn(Throwable.class::isInstance).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inbox_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event_quarantine", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reason_code from audit_event_quarantine", String.class))
                .isEqualTo("CONFLICTING_EVENT_PAYLOAD");
        assertThat(jdbc.queryForObject("select duplicate_count from inbox_event where event_id = ?",
                Integer.class, eventId)).isZero();
        assertThat(jdbc.queryForObject("select conflict_count from inbox_event where event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void concurrentPhase2SameEventIdDifferentPayloadPrefersPayloadConflict() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventEnvelope first = phase2ValidatedEvent(
                eventId,
                "VALIDATED",
                1,
                "sha256:14894f822eb1e00eada33940c2160598c8bde2865e1ddfb2959092a175e4c2fa"
        );
        EventEnvelope second = phase2ValidatedEvent(
                eventId,
                "REJECTED",
                3,
                "sha256:7cf1789c91ec7ca3a4d1f1bb539283206bfd66318055e6aa8f4b5c3e7bb98dab"
        );

        var results = runConcurrently(
                () -> {
                    ingestion.ingest(first);
                    return null;
                },
                () -> {
                    ingestion.ingest(second);
                    return null;
                }
        );

        assertThat(results).filteredOn(Throwable.class::isInstance).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inbox_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from agent_run_audit", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event_quarantine", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reason_code from audit_event_quarantine", String.class))
                .isEqualTo("CONFLICTING_EVENT_PAYLOAD");
        assertThat(jdbc.queryForObject("select duplicate_count from inbox_event where event_id = ?",
                Integer.class, eventId)).isZero();
        assertThat(jdbc.queryForObject("select conflict_count from inbox_event where event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    private EventEnvelope submittedEvent(UUID eventId, UUID applicationId, String applicantId) {
        return new EventEnvelope(
                eventId,
                "loan.application.submitted.v1",
                "1.1.0",
                Instant.parse("2026-01-01T00:00:00Z"),
                "loan-service",
                applicationId,
                applicationId.toString(),
                null,
                applicationId.toString(),
                null,
                objectMapper.valueToTree(Map.of(
                        "applicationId", applicationId.toString(),
                        "applicantId", applicantId,
                        "inputSnapshotVersion", "snapshot-v1",
                        "submittedAt", "2026-01-01T00:00:00Z",
                        "submissionChannel", "PARTNER_API"
                ))
        );
    }

    private EventEnvelope phase2ValidatedEvent(UUID eventId, String outcome, int attemptId, String resultDigest) {
        UUID applicationId = UUID.fromString("10000000-0000-4000-8000-000000002001");
        UUID evaluationRunId = UUID.fromString("30000000-0000-4000-8000-000000002001");
        UUID agentRunId = UUID.fromString("60000000-0000-4000-8000-000000002001");
        List<String> reasonCodes = "VALIDATED".equals(outcome)
                ? List.of("SCHEMA_VALID", "MODEL_PROVENANCE_VALID", "SNAPSHOT_MATCHED", "SHAP_PRESENT")
                : List.of("MODEL_PROVENANCE_INVALID", "AGENT_FAILURE_RECORDED");
        return new EventEnvelope(
                eventId,
                "governance.agent-result.validated.v1",
                "1.0.0",
                Instant.parse("2026-07-21T01:10:04Z"),
                "governance-service",
                applicationId,
                "case-2001",
                evaluationRunId,
                applicationId.toString(),
                agentRunId,
                objectMapper.valueToTree(Map.of(
                        "decisionCaseId", "case-2001",
                        "evaluationRunId", evaluationRunId.toString(),
                        "agentRunId", agentRunId.toString(),
                        "attemptId", attemptId,
                        "agentResultReference", "agent-result://case-2001/" + agentRunId + "/attempt-" + attemptId,
                        "agentResultDigest", resultDigest,
                        "validationOutcome", outcome,
                        "validationReasonCodes", reasonCodes,
                        "validatedSchemaVersion", "1.0.0",
                        "validatedAt", "2026-07-21T10:10:04+09:00"
                ))
        );
    }

    private List<Object> runConcurrently(ThrowingSupplier<?> first, ThrowingSupplier<?> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Object> left = executor.submit(() -> callAfterStart(first, ready, start));
            Future<Object> right = executor.submit(() -> callAfterStart(second, ready, start));
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            results.add(left.get());
            results.add(right.get());
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Object callAfterStart(ThrowingSupplier<?> supplier, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return supplier.get();
        } catch (Exception exception) {
            return exception;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
