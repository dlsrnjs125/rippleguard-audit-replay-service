package dev.rippleguard.audit.infrastructure.kafka;

import dev.rippleguard.audit.application.AuditIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rippleguard.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class AuditEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditIngestionService ingestionService;

    public AuditEventConsumer(AuditIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = {
            "${rippleguard.kafka.topics.loan-application-submitted}",
            "${rippleguard.kafka.topics.governance-review-started}",
            "${rippleguard.kafka.topics.agent-evaluation-requested}",
            "${rippleguard.kafka.topics.agent-evaluation-completed}",
            "${rippleguard.kafka.topics.governance-agent-result-validated}",
            "${rippleguard.kafka.topics.loan-decision-commanded}",
            "${rippleguard.kafka.topics.loan-decision-finalized}"
    })
    public void consume(String rawMessage) {
        if (ingestionService.tryIngestRaw(rawMessage)) {
            log.info("Consumed audit event");
            return;
        }
        log.warn("Quarantined malformed audit event");
    }
}
