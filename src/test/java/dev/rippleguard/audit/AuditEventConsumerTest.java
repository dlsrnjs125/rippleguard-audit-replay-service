package dev.rippleguard.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.rippleguard.audit.application.AuditIngestionService;
import dev.rippleguard.audit.infrastructure.kafka.AuditEventConsumer;
import org.junit.jupiter.api.Test;

class AuditEventConsumerTest {
    @Test
    void malformedJsonIsQuarantinedAtConsumerBoundary() {
        AuditIngestionService ingestion = org.mockito.Mockito.mock(AuditIngestionService.class);
        AuditEventConsumer consumer = new AuditEventConsumer(ingestion);
        String malformed = "{\"eventId\":\"not-a-uuid\"";

        when(ingestion.tryIngestRaw(malformed)).thenReturn(false);

        assertThatCode(() -> consumer.consume(malformed)).doesNotThrowAnyException();
        verify(ingestion).tryIngestRaw(malformed);
    }
}
