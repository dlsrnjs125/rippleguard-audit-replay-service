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
