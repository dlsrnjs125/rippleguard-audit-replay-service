# Phase 2 Agent Run Timeline

This service consumes Governance-owned Phase 2 validation events and projects a minimal audit read model. Governance remains the source of truth for Decision Case, Evaluation Run, and Agent Result validation state.

Implemented boundary:

- `governance.agent-result.validated.v1` consumer topic configuration
- bundled official JSON Schema validation for schema version `1.0.0`
- producer check for `governance-service`
- correlation and causation checks where `governance.agent-result.validated.v1.causationId` must reference a persisted predecessor event id
- inbox idempotency with duplicate and conflict separation
- bounded `PENDING_CAUSATION` storage for cross-topic validation events that arrive before their request event
- `audit_event_quarantine` for malformed, invalid, unsupported, broken, and conflicting events
- `agent_run_audit` and `agent_attempt_audit` projection
- case timeline summary for validated and rejected Agent Results
- `GET /api/v1/agent-runs/{agentRunId}`
- `GET /api/v1/evaluation-runs/{evaluationRunId}/agent-runs`

Excluded boundary:

- no replay execution
- no Agent Runtime recall
- no Governance or Loan state mutation
- no Agent Result body digest recomputation
- no result digest override
- no model artifact download
- no missing metadata defaults
- no hash chain, Merkle tree, version diff, or graph API

Known limitation:

The current Governance validation event carries result reference, result digest, validation outcome, and terminal attempt identity. It does not carry the full request/result metadata for model version, model artifact reference, snapshot identity, feature schema, preprocessing version, or threshold version. Those read-model fields are intentionally nullable until a future Governance audit event carries them.

Phase 2 validation events no longer treat `agentRunId` as a predecessor. Governance-owned predecessors, such as `agent.evaluation.requested.v1`, are published before dependent validation events by the Governance outbox ordering guard, but Kafka only preserves order within one topic partition. Since request and validation events use separate topics, Audit stores validation events whose `causationId` is absent from `audit_event` as bounded `PENDING_CAUSATION`. Pending events are not normal `audit_event` rows, do not create Agent Run projections, and are not shown in normal timelines. If the predecessor arrives before expiry, Audit revalidates and resolves the original event. If the wait expires or attempts are exhausted, Audit quarantines it as `BROKEN_CAUSATION`. Audit does not query Governance state, create a synthetic predecessor, or substitute `correlationId`.

The service preserves and validates the shape of `agentResultDigest`, and detects conflicting digests for the same `agentRunId`. It does not recompute the digest from the Agent Result body because that body is not part of this audit event.

The current projection treats `agentRunId` as having one terminal validation event. If Governance later emits multiple validation events for retries under the same `agentRunId`, the read model must be changed to append attempts and update the aggregate instead of quarantining the later terminal event as a conflict.
