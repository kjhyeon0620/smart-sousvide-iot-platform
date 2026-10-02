---
issue: TBD
title: 서버 제어 정책을 자동 제어에 연결
status: draft
size: M
type: feature
branch: feature/#<issue>-control-policy-auto-control
---

# 서버 제어 정책을 자동 제어에 연결

> 문서를 개편하면서 코드를 대조하다 발견한 격차다 ([architecture §9](../../architecture.md#9-알려진-격차)). SDD 파일럿 후보다.

## 목적 / 성공 조건
- 목적: 사용자가 대시보드나 API로 바꾼 `targetTemp`/`hysteresis`가 실제 자동 제어에 반영된다.
- 기술적 완료: `ControlDecisionEngine`의 판단이 서버에 저장된 정책을 기준으로 이루어진다 (정책이 없을 때의 규칙은 Q1).
- 사용성: 대시보드에서 정책을 저장한 뒤 다음 telemetry부터 자동 명령이 새 기준을 따른다.

## 현재 동작 (코드 기준)
- `PATCH /devices/{id}/control-policy`는 `devices.control_target_temp`, `control_hysteresis`에 저장만 한다.
- `ControlDecisionEngine.decide(message)`는 **기기 payload의 `targetTemp`**와 **전역 `control.deadband`(0.3)**로 판단한다.
- `state=OFF`면 `HEAT_OFF`, 하한 미만이면 `HEAT_ON`, 상한 초과면 `HEAT_OFF`, 그 사이면 `HOLD`(발행하지 않음)

## 범위
- 포함: 정책 조회 경로, 판단 로직 변경, 테스트, 문서
- 제외: 정책 변경 이력, 기기로 목표 온도를 downlink하는 것 (Q3), CookSession

## 입출력·계약
- HTTP/MQTT 계약 변경 없음 (예정)
- 판단 입력: `(message.temp, message.state, policy.targetTemp, policy.hysteresis)`

## 제약
- ingestion 경로에서 기기마다 MySQL을 조회하면 고부하에서 병목이 된다 ([ADR-0003](../../adr/0003-executor-inbound-channel.md) 측정 조건). 정책 조회 비용을 고려해야 한다 (Q2).
- `ControlDecisionEngine`은 순수 로직으로 유지한다 ([ADR-0001](../../adr/0001-ingestion-architecture.md)).

## 예외·경계 상황
| 상황 | 시스템 동작 |
|---|---|
| 정책이 설정되지 않은 기기 | Q1 |
| 미등록 기기의 telemetry | 기존대로 자동 발행 대상에서 제외 |
| payload `targetTemp`와 서버 정책이 다름 | Q1 |
| 정책을 바꾼 직후 | 다음 telemetry부터 반영 (캐시를 쓰면 TTL만큼 지연, Q2) |

## 미결 질문 (Open)
- [ ] Q1. 기준 우선순위: 서버 정책이 있으면 서버 정책을 쓰고, 없으면 payload `targetTemp` + 전역 deadband로 fallback하는가?
- [ ] Q2. 정책 조회 방식: 매번 DB 조회 / 로컬 캐시(TTL) / Redis 캐시 중 무엇으로 할지. 허용할 반영 지연은?
- [ ] Q3. 서버 정책의 `targetTemp`를 기기에도 내려보내야 하는가? (현재 downlink 명령에는 목표 온도가 없다)
- [ ] Q4. `hysteresis`를 deadband와 같은 의미(±)로 볼 것인가?

## 결정 기록 (Decisions)
-

## 완료 증거
| 증명할 것 | 방법 |
|---|---|
| 정책 기준으로 HEAT_ON/HEAT_OFF/HOLD 판단 | `ControlDecisionEngineTest` 케이스 추가 |
| 정책이 없을 때의 fallback | 단위 테스트 |
| ingestion에서 정책이 반영된 자동 명령 생성 | `DeviceIngestionServiceImplTest` |
| 부하 영향 | (Q2 결과에 따라) bypass 1k 재측정 결과를 기존 결과와 비교 |

## 회고
-
