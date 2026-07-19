# Audit Foundation

Phase 1 audit stores immutable event facts from Loan and Governance services. It does not own loan or governance business state and never connects to their databases.

Consumed events:

- `loan.application.submitted.v1`
- `governance.review.started.v1`
- `agent.evaluation.requested.v1`
- `agent.evaluation.completed.v1`
- `loan.decision.commanded.v1`
- `loan.decision.finalized.v1`

The current implementation records unknown schema versions instead of treating them as completed support.

Deferred to later phases:

- Replay
- Hash chain
- Version diff
- Execution graph
- Golden case tooling
