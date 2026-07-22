package dev.rippleguard.audit.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunAuditRepository extends JpaRepository<AgentRunAuditEntity, UUID> {
    List<AgentRunAuditEntity> findByEvaluationRunIdOrderByValidatedAtAscAgentRunIdAsc(UUID evaluationRunId);

    Optional<AgentRunAuditEntity> findBySourceEventId(UUID sourceEventId);
}
