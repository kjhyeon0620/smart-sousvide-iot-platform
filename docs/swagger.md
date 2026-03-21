# Swagger(OpenAPI) Guide

## Purpose
- 브라우저에서 API를 빠르게 탐색/호출해 수동 검증 효율을 높인다.

## Endpoints
- Swagger UI:
  - `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON:
  - `http://localhost:8080/v3/api-docs`
- Grouped API(JSON):
  - `http://localhost:8080/v3/api-docs/device-api`

## How To Use
1. 애플리케이션 실행
2. 브라우저에서 `/swagger-ui.html` 접속
3. `device-api` 그룹 선택
4. 원하는 endpoint의 `Try it out`으로 요청 테스트

## Notes
- Actuator endpoint는 `device-api` 그룹에 포함되지 않는다.
- 실제 데이터 검증은 `docs/device-api.md`와 함께 사용한다.
- 프로젝트 개요와 실행 흐름은 루트 `README.md`를 기준으로 확인한다.
