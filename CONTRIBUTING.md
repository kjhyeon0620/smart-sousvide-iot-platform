# Contributing Guide

## Branch Strategy
- `main`: 배포 기준 브랜치(직접 푸시 금지)
- 작업 브랜치 prefix:
  - `feature`: 사용자 기능 추가
  - `techdebt`: 내부 구조 개선/리팩토링/성능 개선
  - `bugfix`: 결함 수정
- 브랜치 네이밍:
  - `<type>/#<issue-number>-<short-kebab-summary>`
  - 예: `feature/#26-device-domain-api-foundation`

## Commit Convention
- Conventional Commits 사용
- 예시: `feat(ingestion): parse device status payload`

## Workflow
1. Issue 생성 후 범위와 완료 조건(DoD) 확정
2. 작업 브랜치 생성
3. 코드/테스트/문서 반영
4. PR 생성 후 템플릿 항목 모두 작성
5. 리뷰 반영 후 머지

## PR Rules
- 최소 1개 이슈 링크 필수
- 테스트 증거(로그/스크린샷) 첨부 필수
- 리스크 및 롤백 방법 명시 필수

## Coding Standards
- Java 17, Spring Boot 기준 준수
- DTO/도메인 타입은 명시적이고 엄격하게 설계
- 예외 경로(파싱 실패/저장 실패) 반드시 처리

## Testing Expectations
- 단위 테스트: 파싱/제어 로직 필수
- 통합 테스트: MQTT 수신 -> Influx/Redis 반영 경로 검증
- 부하 테스트: 시뮬레이터 기반 동시 연결 시나리오 별도 관리
