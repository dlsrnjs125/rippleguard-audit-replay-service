# RippleGuard Audit Replay Service

시스템과 AI 판단의 감사 추적, 재현, 조회 모델을 담당하는 서비스입니다.

업무 상태의 원본은 각 도메인 서비스가 소유하며 이 서비스는 감사와 재현 가능한 조회 기반을 담당합니다. 현재 구현은 Loan/Governance 이벤트를 안전하게 수집하고 케이스 타임라인 API와 Phase 2 Agent Run 조회 API를 제공합니다.

Audit Replay Service는 Loan Service나 Governance Service의 상태를 수정하지 않습니다. Phase 2에서는 Governance Service가 발행한 `governance.agent-result.validated.v1`만 Agent Result 검증의 정상 source로 사용하며, Agent Runtime 결과를 직접 소비해 Governance 판정을 우회하지 않습니다.

## Phase 2 Agent Run Timeline

- consumes `governance.agent-result.validated.v1` from Governance Service
- validates the bundled `rippleguard-contracts` JSON Schema before projection
- rejects invalid producer, unsupported schema, broken causation, result reference mismatch, and conflicting event payloads into quarantine
- stores Agent Run and Attempt read models for `VALIDATED` and `REJECTED`
- preserves result reference and digest without recomputing the Agent Result body digest or overwriting Governance's outcome
- leaves model, snapshot, feature, preprocessing, and threshold metadata nullable when the validation event does not carry those fields

This is not the Phase 7 replay engine. It does not run replay, call Agent Runtime, recalculate SHAP, download model artifacts, build hash chains, or create graph APIs.

## Run

```bash
make test
make package
cp .env.example .env
# Fill .env with local secret values.
make run-local
make build-image
```

`make build-image` packages the service and builds
`rippleguard-audit-replay-service:<commit-sha-12>`. The image records
`org.opencontainers.image.revision` as the full Git commit SHA and
`org.opencontainers.image.source` as this repository URL. After this PR is
merged, build the final Audit & Replay Service image from the new `main` merge
commit in this repository. RippleGuard Infra records and verifies the immutable
image baseline; Infra does not own the Audit image build.

## API

`GET /api/v1/cases/{caseId}/timeline`

`GET /api/v1/agent-runs/{agentRunId}`

`GET /api/v1/evaluation-runs/{evaluationRunId}/agent-runs`

Quarantined events are not returned as normal timeline or Agent Run projection rows.

## Documentation

Detailed implementation notes are in `docs/`.
