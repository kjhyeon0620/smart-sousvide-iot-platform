---
issue: 55
title: 사용자 기기 대시보드 MVP
status: done
size: L
type: feature
branch: feature/#55-user-device-dashboard-mvp
---

# 사용자 기기 대시보드 MVP

> SDD를 도입하기 전에 진행한 이슈를 기존 `docs/user-device-dashboard.md`와 로컬 기획 노트(2026-05-05)를 바탕으로 역작성한 스펙이다. 후속 배포 수정은 #60에서 했다.

## 목적 / 성공 조건
- 목적: 사용자가 자기 수비드 기기의 상태를 빠르게 파악하고, 온도 흐름을 보고, 제어 정책과 명령을 안전하게 다룬다. 운영자용 관측성 화면과는 분리한다.
- 기술적 완료: 기존 API만으로 목록과 상세 화면이 동작하고, `npm run build`가 통과한다.
- 사용성: offline이거나 disabled인 기기에서는 명령 버튼이 막히고 이유가 표시된다. 명령 실패, 만료, 지연 상태를 명령 이력에서 확인할 수 있다.

## 범위
- 포함
  - 목록: 요약(전체, online, offline, heating), 기기 카드(online/offline, enabled, 현재 온도, 목표 온도, 상태, last seen), 필터(all/online/offline/disabled/heating)
  - 상세: 현재 상태, 온도 차트(`temp`/`targetTemp`, 1h/6h/24h), 제어 정책 폼, enabled 토글, 명령 버튼(`HEAT_ON`/`HEAT_OFF`/`HOLD`), 명령 이력
  - 상태: loading, empty, error, offline, command failure, pending/expired
  - 배포: Docker 빌드에서 Spring static으로 번들 (#60)
- 제외: JWT/Auth, 소유권, CookSession, 알림, SSE/WebSocket, 기기 등록 UI, 관리자 대시보드, 커스텀 위젯, 네이티브 앱

## 입출력·계약
- 기존 endpoint만 사용한다. API 계약 변경 없음.
  - `GET /devices`, `GET /devices/{id}`, `PATCH /devices/{id}/enabled`, `GET /devices/{id}/status`, `GET /devices/{id}/temps`, `GET|PATCH /devices/{id}/control-policy`, `POST|GET /devices/{id}/commands`
- 라우트: `/devices` 목록 → 앱 내부 상세 화면 이동
- 수동 명령 idempotency key: `dashboard:{id}:{commandType}:{Date.now()}`

## 제약
- [ADR-0004](../../adr/0004-user-dashboard-delivery.md)
- Spring API 계약을 바꾸지 않는다.

## 예외·경계 상황
| 상황 | 사용자가 보는 것 | 시스템 동작 |
|---|---|---|
| 등록된 기기 없음 | empty 상태 | - |
| telemetry 없음 | 온도 값 없음, 차트 empty | - |
| offline / disabled | 배지와 명령 버튼 비활성, 이유 표시 | 명령 요청을 보내지 않음 |
| 시계열 조회 실패 | 차트 error 상태 | 다음 polling에서 재시도 |
| 정책 저장 실패 | 폼 에러 | 입력값 유지 |
| 명령 발행 실패 / ACK 만료 | 이력에 FAILED / EXPIRED 표시 | 백엔드 신뢰성 스케줄러가 처리 |

## 미결 질문 (Open)
- 없음

## 결정 기록 (Decisions)
- 2026-05-05 Web만 만들 것인가? → Web dashboard first.
- 2026-05-05 MVP에서 인증을 뺄 것인가? → 뺀다. 소유권과 JWT는 별도 이슈로 분리한다. [승격: ADR-0004]
- 2026-05-05 기기 등록을 MVP에 넣을 것인가? → 넣지 않는다. 후속 이슈로 둔다.
- 2026-05-05 실시간 갱신 방식은? → polling으로 한다. 목록 30s, 상세 5s, 차트 30s. [승격: ADR-0004]
- 2026-05-05 차트 기본 범위는? → 1h (선택지 1h/6h/24h)
- 2026-05-05 CookSession을 첫 대시보드에 넣을 것인가? → 넣지 않는다. 후속으로 둔다.
- 기본값 가정: 명령 버튼은 확인 모달 없이 즉시 실행하고, offline/disabled일 때만 막는다.
- 2026-09 (#60) 운영 이미지에 대시보드를 포함한다. root Dockerfile의 Node 빌드 단계 → `BOOT-INF/classes/static/`

## 완료 증거
| 증명할 것 | 방법 |
|---|---|
| 빌드 | `cd frontend && npm ci && npm run build` |
| 목록 요약이 `/devices` + status와 일치 | 백엔드 실행 후 수동 확인 |
| 필터 5종 동작 | 수동 확인 |
| 정책 저장 / enabled 토글 / 명령 3종이 해당 API 호출 | 브라우저 네트워크 탭 |
| offline/disabled에서 명령 차단 | 수동 확인 |
| error 상태, 모바일 레이아웃 | [error-desktop](screenshot-list-error-desktop.png), [mobile](screenshot-list-mobile.png) |
| 운영 이미지에서 `/` = 대시보드, `/assets/*` 로드, `/devices` = JSON | 이미지 빌드 후 확인 (#60) |

## 회고
- 어긋난 점: 첫 PR 머지 후 운영 이미지에 대시보드가 빠져 있어서 #60에서 bugfix가 필요했다.
- 원인 분류: 완료 기준 느슨. 완료 증거에 "배포 산출물에서 확인"이 없었다.
- 고친 위치: 스펙 템플릿의 완료 증거에 배포 경로 확인을 넣는 관행을 두고, [`architecture.md` §3.4](../../architecture.md)에 번들 방식을 명시했다.
- 상태별 화면을 확인하려고 Redis/Influx/MySQL을 수동으로 조작해야 했다. → [demo data console 초안](../0057-dashboard-demo-data-console/spec.md)
