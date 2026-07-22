# Phase 2 Agent Run Timeline

This service consumes Governance-owned Phase 2 validation events and projects a minimal audit read model. Governance remains the source of truth for Decision Case, Evaluation Run, and Agent Result validation state.

Implemented boundary:

- `governance.agent-result.validated.v1` consumer topic configuration
- bundled official JSON Schema validation for schema version `1.0.0`
- producer check for `governance-service`
- correlation and causation checks without creating synthetic predecessor events
- inbox idempotency with duplicate and conflict separation
- `audit_event_quarantine` for malformed, invalid, unsupported, broken, and conflicting events
- `agent_run_audit` and `agent_attempt_audit` projection
- case timeline summary for validated and rejected Agent Results
- `GET /api/v1/agent-runs/{agentRunId}`
- `GET /api/v1/evaluation-runs/{evaluationRunId}/agent-runs`

Excluded boundary:

- no replay execution
- no Agent Runtime recall
- no Governance or Loan state mutation
- no result digest override
- no model artifact download
- no missing metadata defaults
- no hash chain, Merkle tree, version diff, or graph API

Known limitation:

The current Governance validation event carries result reference, result digest, validation outcome, and attempt identity. It does not carry the full request/result metadata for model version, model artifact reference, snapshot identity, feature schema, preprocessing version, or threshold version. Those read-model fields are intentionally nullable until a future Governance audit event carries them.
