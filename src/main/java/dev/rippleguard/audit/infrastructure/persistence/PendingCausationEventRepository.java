package dev.rippleguard.audit.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingCausationEventRepository extends JpaRepository<PendingCausationEventEntity, UUID> {
    @Query(value = """
            select *
            from pending_causation_event
            where causation_id = :causationId
              and status = 'PENDING_CAUSATION'
            order by first_seen_at
            for update skip locked
            """, nativeQuery = true)
    List<PendingCausationEventEntity> claimByCausationId(@Param("causationId") UUID causationId);

    @Query(value = """
            select *
            from pending_causation_event
            where status = 'PENDING_CAUSATION'
              and next_attempt_at <= :now
            order by first_seen_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<PendingCausationEventEntity> claimDueForReconciliation(@Param("now") Instant now,
                                                                @Param("batchSize") int batchSize);

    @Query(value = """
            select *
            from pending_causation_event
            where status = 'PENDING_CAUSATION'
              and expires_at <= :now
            order by first_seen_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<PendingCausationEventEntity> claimExpired(@Param("now") Instant now,
                                                   @Param("batchSize") int batchSize);

    @Query(value = """
            select *
            from pending_causation_event
            where status = 'PENDING_CAUSATION'
              and next_attempt_at <= :now
              and attempt_count >= :maxAttempts
            order by first_seen_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<PendingCausationEventEntity> claimAttemptExhausted(@Param("now") Instant now,
                                                            @Param("maxAttempts") int maxAttempts,
                                                            @Param("batchSize") int batchSize);
}
