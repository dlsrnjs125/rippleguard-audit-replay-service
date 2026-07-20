# RippleGuard Audit Replay Service

시스템과 AI 판단의 감사 추적, 재현, 조회 모델을 담당하는 서비스입니다.

업무 상태의 원본은 각 도메인 서비스가 소유하며 이 서비스는 감사와 재현 가능한 조회 기반을 담당합니다. 현재 구현은 원본 이벤트를 안전하게 수집하고 케이스 타임라인 API를 제공합니다.

## Run

```bash
./mvnw test
./mvnw package
./scripts/build-image.sh
```

`scripts/build-image.sh` packages the service and builds
`rippleguard-audit-replay-service:<commit-sha-12>`. The image records
`org.opencontainers.image.revision` as the full Git commit SHA and
`org.opencontainers.image.source` as this repository URL. After this PR is
merged, build the final Audit & Replay Service image from the new `main` merge
commit in this repository. RippleGuard Infra records and verifies the immutable
image baseline; Infra does not own the Audit image build.

## API

`GET /api/v1/cases/{caseId}/timeline`

## Documentation

Detailed implementation notes are in `docs/`.
