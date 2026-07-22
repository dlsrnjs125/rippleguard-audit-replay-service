package dev.rippleguard.audit.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventQuarantineRepository extends JpaRepository<AuditEventQuarantineEntity, UUID> {
}
