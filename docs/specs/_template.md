---
issue: TBD            # GitHub 이슈 번호
title:
status: draft         # draft | ready | in-progress | done | dropped
size: M               # S | M | L
type: feature         # feature | techdebt | bugfix
branch:               # <type>/#<issue>-<slug>
---

# <title>

## 목적 / 성공 조건
- 목적: (누가 무엇을 할 수 있게 되는가)
- 기술적 완료: (정상 입력에 시스템이 무엇을 하는가)
- 사용성·운영: (실패했을 때 사용자나 운영자가 무엇을 알 수 있고, 어떻게 이어서 진행하는가)

## 범위
- 포함:
- 제외: (이번에 만들지 않는 것. 명시하지 않으면 구현자가 범위를 넓힐 수 있음)

## 입출력·계약
- HTTP: `METHOD /path` 요청 필드, 응답 필드, 에러 코드 → 확정되면 `docs/device-api.md`에 반영
- MQTT: topic, payload → 확정되면 `docs/architecture.md`에 반영
- 저장소: MySQL 테이블/컬럼, Redis 키, Influx measurement/field
- 상태 전이:

## 제약
- 참조: [architecture](../../architecture.md), [ADR-0001](../../adr/0001-ingestion-architecture.md)
- (이 이슈에만 적용되는 제약)

## 예외·경계 상황
| 상황 | 사용자·운영자가 보는 것 | 시스템 동작 |
|---|---|---|
| 잘못된 입력 | | |
| 중복 요청 | | |
| 기기 offline / disabled | | |
| 저장소(MySQL/Redis/Influx) 장애 | | |
| MQTT broker 장애 | | |

## 미결 질문 (Open)
- [ ] Q1.

## 결정 기록 (Decisions)
- YYYY-MM-DD Q1 → 답. (이유) [승격: ADR-NNNN / architecture]

## 완료 증거
| 증명할 것 | 방법 (명령·테스트·수동 시나리오) |
|---|---|
| | `./gradlew test --tests '...'` |

## Tasks (L 등급만)
### T1.
- 먼저 읽을 것:
- 변경:
- 금지:
- 검증:

## 운영 반영
- (머지 후 배포 workflow 결과. 다음 이슈 PR에서 기록)

## 회고
- 어긋난 점:
- 원인 분류: 스펙 누락 / 규칙 미전달 / 테스트 부재 / 외부 제약 미인지 / 완료 기준 느슨
- 고친 위치:
