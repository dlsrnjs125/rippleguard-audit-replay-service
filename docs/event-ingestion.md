# Event Ingestion

Kafka consumption is configured for the Phase 1 topics plus `governance.agent-result.validated.v1`. Each event is parsed as the common event envelope and written through JPA/Flyway-managed tables.

For Phase 2 validation events, the service loads bundled `rippleguard-contracts` schemas from `src/main/resources/contracts` and validates `events/governance.agent-result.validated.v1.0.0.schema.json` before projection. Runtime schema downloads are not used.

Idempotency:

- `audit_event.event_id` is the immutable unique event key
- `inbox_event.event_id` prevents duplicate event storage
- repeated events increment `inbox_event.duplicate_count`
- same `eventId` with a different canonical envelope hash increments `inbox_event.conflict_count` and writes quarantine instead of overwriting the existing projection
- concurrent duplicate insert races are recovered after rollback in a separate transaction

Out-of-order delivery is expected for the general timeline. The service does not rewrite original events. Timeline warnings are computed at query time.

Phase 2 validation causation is stricter: `governance.agent-result.validated.v1` must point at a real predecessor event id. Because the request and validation events are consumed from different Kafka topics, Audit does not assume cross-topic delivery order. If the predecessor is missing when Audit ingests the validation event, Audit stores it in `pending_causation_event` with bounded retry metadata and does not create `audit_event`, Agent Run, Attempt, or Timeline projection rows. Reconciliation claims pending rows with PostgreSQL `FOR UPDATE SKIP LOCKED` so a scheduler and predecessor ingestion cannot project the same validation event concurrently. When the predecessor later arrives, Audit revalidates the original validation envelope, creates the normal projections with the original `occurredAt`, and marks the pending row `RESOLVED`. If the bounded wait expires or retry attempts are exhausted, Audit writes `BROKEN_CAUSATION` quarantine.

`pending_causation_event.raw_payload` is retained after `RESOLVED` or `EXPIRED` for audit forensics and replay traceability. A resolved row therefore duplicates the accepted `audit_event` payload hash and sanitized projection until an explicit retention/archive policy is introduced; expired rows retain the original raw envelope that produced the quarantine decision. The Phase 2 validation payload is intentionally limited to event metadata, result references, digests, outcome, and version provenance.

Invalid Phase 2 validation events are stored in `audit_event_quarantine` with a bounded reason code and safe payload hash. Contract-invalid events are not blindly retried and do not create normal Agent Run, Attempt, or timeline projections.
