# Architecture Decision Records

되돌리는 비용이 큰 기술 결정과 그 이유, 그리고 결정 때문에 감수하는 점을 기록한다.
스펙의 Decisions 중에서 **여러 이슈에 영향을 주는 결정**을 여기로 옮긴다.

| ADR | 제목 | Status |
|---|---|---|
| [0001](0001-ingestion-architecture.md) | Ingestion Architecture for MQTT Device Status | Accepted |
| [0002](0002-downlink-command-reliability.md) | Downlink Command Reliability Model | Accepted |
| [0003](0003-executor-inbound-channel.md) | Executor-backed MQTT Inbound Channel | Accepted |
| [0004](0004-user-dashboard-delivery.md) | User Dashboard Delivery (Bundled SPA, Polling, No Auth in MVP) | Accepted |

## 규칙

- 파일 이름은 `NNNN-<kebab-slug>.md`, 번호는 순서대로 붙인다.
- Status: `Proposed` | `Accepted` | `Superseded by NNNN` | `Deprecated`
- 결정을 뒤집을 때는 기존 ADR을 수정하지 않는다. 새 ADR을 쓰고 기존 ADR의 Status만 바꾼다.
- 결정을 만든 스펙이 있으면 링크한다.

## 템플릿

```markdown
# ADR-NNNN: <제목>

## Status
Proposed

## Context
- 문제, 제약, 요구

## Decision
- 무엇을 하기로 했는가

## Alternatives
- 검토한 대안과 채택하지 않은 이유

## Consequences
- 장점:
- 감수하는 점:

## Revisit When
- 이 결정을 다시 검토해야 하는 조건

## References
- spec / 이슈 / 측정 결과
```
