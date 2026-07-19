# RippleGuard Audit Replay Service

시스템과 AI 판단의 감사 추적, 재현, 조회 모델을 담당하는 서비스입니다.

업무 상태의 원본은 각 도메인 서비스가 소유하며 이 서비스는 감사와 재현 가능한 조회 기반을 담당합니다. 현재 구현은 원본 이벤트를 안전하게 수집하고 케이스 타임라인 API를 제공합니다.

## Run

```bash
./mvnw test
./mvnw package
docker build -t rippleguard-audit-replay-service:local .
```

## API

`GET /api/v1/cases/{caseId}/timeline`

## Documentation

Detailed implementation notes are in `docs/`.
