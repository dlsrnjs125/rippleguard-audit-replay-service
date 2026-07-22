# Event Ingestion

Kafka consumption is configured for the Phase 1 topics plus `governance.agent-result.validated.v1`. Each event is parsed as the common event envelope and written through JPA/Flyway-managed tables.

For Phase 2 validation events, the service loads bundled `rippleguard-contracts` schemas from `src/main/resources/contracts` and validates `events/governance.agent-result.validated.v1.0.0.schema.json` before projection. Runtime schema downloads are not used.

Idempotency:

- `audit_event.event_id` is the immutable unique event key
- `inbox_event.event_id` prevents duplicate event storage
- repeated events increment `inbox_event.duplicate_count`
- same `eventId` with a different canonical envelope hash increments `inbox_event.conflict_count` and writes quarantine instead of overwriting the existing projection
- concurrent duplicate insert races are recovered after rollback in a separate transaction

Out-of-order delivery is expected. The service does not rewrite original events. Timeline warnings are computed at query time.

Invalid Phase 2 validation events are stored in `audit_event_quarantine` with a bounded reason code and safe payload hash. Contract-invalid events are not blindly retried and do not create normal Agent Run, Attempt, or timeline projections.
