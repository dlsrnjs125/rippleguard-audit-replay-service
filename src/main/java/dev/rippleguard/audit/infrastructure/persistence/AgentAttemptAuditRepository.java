package dev.rippleguard.audit.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAttemptAuditRepository extends JpaRepository<AgentAttemptAuditEntity, AgentAttemptAuditEntity.Key> {
    List<AgentAttemptAuditEntity> findByAgentRunIdOrderByAttemptIdAsc(UUID agentRunId);
}
