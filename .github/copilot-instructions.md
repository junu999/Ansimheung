# Copilot 지침

전체 규칙은 저장소 루트의 `AGENTS.md`, 정책·알고리즘은 `docs/domain.md`, API 계약은 `docs/api.md`에 있다. 핵심만:

- Spring Boot 3.5 / Java 21 / PostgreSQL+PostGIS / Flyway. 프론트는 `src/main/resources/static/` 순수 JS ES 모듈 + Leaflet (npm·React 없음).
- 오류는 `throw new ApiException(ErrorCode.XXX)`. 정책 숫자는 `AnsimheungProperties`·`common/type` enum에서만 읽기 (하드코딩 금지).
- enum 값은 `ILLEGAL_PARKING`, `MORNING`, `RECEIVED`, `SHORTEST` 같은 대문자 영문. 응답은 camelCase, 시간은 `+09:00` 포함.
- 좌표: JSON `lat`/`lng`, GeoJSON `[lng, lat]`, Leaflet `[lat, lng]`.
- 응답 모양은 `static/mock/*.json`과 같게. 프론트는 `fetch` 대신 `js/api.js` 사용.
- 마이그레이션 V1 수정 금지(새 `V2__*.sql`), 경로 출발지 좌표 저장·로그 금지, 새 의존성 추가 금지.
