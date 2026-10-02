# ADR-0004: User Dashboard Delivery (Bundled SPA, Polling, No Auth in MVP)

## Status
Accepted (#55, #60에서 구현)

## Context
- 백엔드 API와 운영용 Grafana는 있었지만 사용자가 자기 기기를 확인하고 제어하는 제품 화면이 없었다.
- 필요한 API는 이미 모두 있었다. 인증과 소유권까지 같이 넣으면 첫 UI PR의 범위가 지나치게 커진다.
- 배포 대상은 K-Le-PaaS가 이미지 하나를 배포하는 구조다.

## Decision
- `frontend/`에 React + Vite + TypeScript SPA를 두고, Docker 빌드에서 Spring `static/`으로 번들한다. 같은 origin에서 `/`와 `/devices`를 제공한다.
- MVP에서는 기존 API만 쓰고 API 계약은 바꾸지 않는다.
- 실시간 갱신은 SSE/WebSocket 대신 polling으로 한다 (목록 30s, 상세 5s, 차트 30s).
- 인증과 소유권은 MVP에서 제외하고 후속 이슈로 분리한다.
- offline이거나 disabled인 기기에는 수동 명령 버튼을 막고 이유를 표시한다.

## Alternatives
- **별도 frontend 배포 (CDN 또는 다른 컨테이너)**: CORS와 배포 파이프라인이 둘로 늘어나서 채택하지 않았다.
- **SSE부터 도입**: polling으로 UX가 충분한지 먼저 확인하기로 했다.

## Consequences
- 장점: 이미지 하나로 배포된다. 백엔드를 바꾸지 않고 제품 화면을 확보했다.
- 감수하는 점:
  - 인증이 없어서 누구나 모든 기기를 조회하고 제어할 수 있다. 외부에 공개 배포하기 전에 소유권 이슈를 먼저 해야 한다.
  - polling 때문에 요청 수가 기기 수에 비례해 늘어난다. 목록 화면에서 기기마다 status를 조회한다.
  - 로컬 `bootJar`에는 frontend가 포함되지 않는다.

## Revisit When
- 외부 사용자에게 공개할 때 (→ DeviceOwnership + JWT)
- 명령 반응성이나 서버 부하가 polling으로 감당되지 않을 때 (→ SSE)

## References
- [spec 0055](../specs/0055-user-device-dashboard-mvp/spec.md)
