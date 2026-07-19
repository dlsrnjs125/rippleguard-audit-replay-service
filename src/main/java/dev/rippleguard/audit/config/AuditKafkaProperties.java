package dev.rippleguard.audit.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rippleguard.kafka")
public record AuditKafkaProperties(boolean enabled, Topics topics) {
    public List<String> topicNames() {
        return List.of(
                topics.loanApplicationSubmitted(),
                topics.governanceReviewStarted(),
                topics.agentEvaluationRequested(),
                topics.agentEvaluationCompleted(),
                topics.loanDecisionCommanded(),
                topics.loanDecisionFinalized()
        );
    }

    public record Topics(String loanApplicationSubmitted,
                         String governanceReviewStarted,
                         String agentEvaluationRequested,
                         String agentEvaluationCompleted,
                         String loanDecisionCommanded,
                         String loanDecisionFinalized) {
    }
}
