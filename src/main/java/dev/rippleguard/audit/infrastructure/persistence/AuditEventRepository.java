package dev.rippleguard.audit.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findByCaseIdOrderByOccurredAtAscIngestedAtAsc(String caseId);
}
