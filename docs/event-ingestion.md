# Event Ingestion

Kafka consumption is configured for the six Phase 1 topics. Each event is parsed as the common event envelope and written through JPA/Flyway-managed tables.

Idempotency:

- `audit_event.event_id` is the immutable unique event key
- `inbox_event.event_id` prevents duplicate event storage
- repeated events increment `inbox_event.duplicate_count`

Out-of-order delivery is expected. The service does not rewrite original events. Timeline warnings are computed at query time.
