# API 계약

> 노션 "API 공통 규약" + "API 명세서" DB(11/08)를 옮긴 것. **응답 예시는 `src/main/resources/static/mock/*.json`이 기준**이며, 프론트(`js/mappers.js`)가 그 모양을 전제로 동작한다.
> 응답 모양을 바꾸면 같은 PR에서 ① 이 문서 ② mock JSON ③ `js/mappers.js`·`src/test/js/mappers.test.mjs`를 함께 고친다.

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| 주소 | 모두 `/api`로 시작 (`/api/v1` 안 씀) |
| 형식 | JSON. 사진이 있는 제보 등록만 `multipart/form-data` |
| 필드 이름 | camelCase (DB 컬럼 snake_case와 다름) |
| 시간 | ISO-8601 + 오프셋: `"2026-11-08T21:30:00+09:00"` (`OffsetDateTime`) |
| 빈 값 | 필드를 빼지 않고 `null` |
| 점수 | 소수 첫째 자리 반올림 (`6.0`) |
| 인증 | 세션 쿠키 `JSESSIONID` (JWT 안 씀) |
| CSRF | POST·PATCH·DELETE는 `X-XSRF-TOKEN` 헤더 = `XSRF-TOKEN` 쿠키 값. 프론트는 `js/http.js`가 자동 처리 |
| 권한 | 불필요 / 회원(미로그인 401) / ADMIN(아니면 403) |
| 페이지 | `?page=0&size=20` → `{content, page, size, totalElements, totalPages}` |
| 좌표 | 필드 `lat`/`lng` · GeoJSON `[lng, lat]` · bbox `서,남,동,북` · WGS84(EPSG:4326) |

## 2. 오류 응답

```json
{ "code": "OUT_OF_AREA", "message": "시범 지역(정왕초 반경 500m) 안에서만 이용할 수 있어요", "fields": null }
```
- `message`는 사용자에게 그대로 보여줄 한국어 문장. `fields`는 `VALIDATION`일 때만 `{필드: 메시지}`.
- 백엔드는 `throw new ApiException(ErrorCode.X)`만 하면 `GlobalExceptionHandler`가 변환. `@Valid` 실패·잘못된 enum·JSON 오류·파일 크기 초과도 자동.

| code | HTTP | 언제 |
|---|---|---|
| `VALIDATION` | 400 | 입력 형식·필수값 오류 |
| `OUT_OF_AREA` | 400 | 정왕초 반경 500m 밖 |
| `FAR_FROM_ROAD` | 400 | 출발지가 가장 가까운 길에서 50m 초과 |
| `OWN_HAZARD` | 400 | 본인 제보 지점에 공감 |
| `FILE_TYPE` | 400 | 사진이 JPG/PNG 아님 |
| `UNAUTHORIZED` | 401 | 로그인 필요 |
| `AUTH_FAIL` | 401 | 이메일 또는 비밀번호 틀림 |
| `FORBIDDEN` | 403 | 권한 없음·본인 것 아님·CSRF 토큰 없음 |
| `NOT_FOUND` | 404 | 없거나 숨김·반려 지점 |
| `NO_ROUTE` | 404 | 그래프 단절로 경로 없음 |
| `METHOD_NOT_ALLOWED` | 405 | Method 틀림 |
| `EMAIL_DUP` | 409 | 이미 가입된 이메일 |
| `DUP_REPORT` | 409 | 같은 지점에 이미 제보 |
| `STATE_CONFLICT` | 409 | 지금 상태에서 불가 (허용 안 된 전이, 접수 아닌 제보 수정·삭제) |
| `FILE_SIZE` | 413 | 사진 5MB 초과 |
| `LIMIT` | 429 | 하루 한도 초과 |
| `INTERNAL` | 500 | 서버 오류 |
| `NO_GRAPH` | 503 | 도보 그래프 미적재 |

## 3. enum 값

| 이름 | 값 |
|---|---|
| `type` | `SIDEWALK_GAP` 보도단절 · `ILLEGAL_PARKING` 불법주정차 · `SPEEDING` 과속차량 · `CONSTRUCTION` 공사구간 · `POOR_LIGHTING` 조명부족 |
| `timeSlot` | `MORNING` 등교 · `AFTERNOON` 하교 · `NIGHT` 야간 · `ALWAYS` 상시 (조회 파라미터에는 ALWAYS 불가) |
| `status` | `RECEIVED` 접수 · `CHECKING` 확인중 · `RESOLVED` 개선완료 · `REJECTED` 반려 |
| `level` | `HIGH` · `MEDIUM` · `RESOLVED` |
| `mode` | `SHORTEST`(λ0) · `BALANCED`(λ20) · `SAFEST`(λ60) |
| `role` | `USER` · `ADMIN` |
| `kind` | `CCTV` · `LIGHT` · `SCHOOL_ZONE` |

## 4. 엔드포인트

이슈 열: 구현 담당 이슈. "범위 제외"는 #28 (이번 제출에서 안 만듦 — 프론트는 404면 조용히 생략).

### 공통 · 인증
| Method | 경로 | 권한 | 요청 | 응답 (mock) | 이슈 |
|---|---|---|---|---|---|
| GET | `/api/health` | 불필요 | — | `{server, database, postgis, postgisDistanceTestM}` | 완료 |
| GET | `/api/config` | 불필요 | — | `config.json` | 완료 |
| POST | `/api/auth/signup` | 불필요 | `{email, password, nickname, ageConfirmed, privacyAgreed}` | 201 `{id}` | #9 |
| POST | `/api/auth/login` | 불필요 | `{email, password}` | `me.json` + 세션 쿠키 | #9 |
| POST | `/api/auth/logout` | 회원 | — | 204 | #9 |
| GET | `/api/me` | 회원 | — | `me.json` `{id, nickname, role}` / 비로그인 401 | #9 |
| GET | `/api/auth/email-check` | 불필요 | `?email=` | `{available}` | 범위 제외 |

### 위험 지점 · 제보
| Method | 경로 | 권한 | 요청 | 응답 (mock) | 이슈 |
|---|---|---|---|---|---|
| GET | `/api/hazards` | 불필요 | `?bbox=서,남,동,북&types=&timeSlot=` | `hazards.json` GeoJSON FeatureCollection. properties = `{id, type, timeSlot, status, score, level, empathyCount, reportCount, createdAt}` | #12 |
| GET | `/api/hazards/{id}` | 불필요 | `?timeSlot=` | `hazard-detail.json` (승인된 사진·이력·`myEmpathy`·`myReported`, 작성자 없음) | #12 |
| GET | `/api/hazards/nearby` | 회원 | `?lat=&lng=&type=` | `hazards-nearby.json` `{candidate \| null}` | #25 (P1) |
| POST | `/api/reports` | 회원 | multipart `lat, lng, type, timeSlot, description?, photo?, mergeHazardId?` | 201 `reports-created.json` `{reportId, hazardId, merged}` | #11 (사진 #24, 묶기 #25) |
| GET | `/api/photos/{id}` | 조건부 | — | image/jpeg·png. 미승인은 본인·ADMIN만, 아니면 404 | #24 (P1) |
| POST | `/api/hazards/{id}/empathy` | 회원 | — | `empathy-on.json` `{empathyCount, myEmpathy:true}` (멱등) | #26 (P1) |
| DELETE | `/api/hazards/{id}/empathy` | 회원 | — | `empathy-off.json` (멱등) | #26 (P1) |
| GET | `/api/me/reports` | 회원 | `?page=&size=` | `me-reports.json` 페이지 | 범위 제외 |
| PATCH | `/api/reports/{id}` | 회원(본인) | `{description?, timeSlot?}` | `{id, description, timeSlot}` | 범위 제외 |
| DELETE | `/api/reports/{id}` | 회원(본인) | — | 204 | 범위 제외 |

### 경로
| Method | 경로 | 권한 | 요청 | 응답 (mock) | 이슈 |
|---|---|---|---|---|---|
| POST | `/api/routes` | 불필요 | `{fromLat, fromLng, timeSlot?}` | `routes.json` `{routes:[{mode, lambda, distanceM, minutes, riskExposure, hazardIds, path(LineString)}] × 3}` | #13 |

- `timeSlot` 생략 시 **요청 시각(Asia/Seoul)의 시간대**로 계산 (09–14시는 시간대 없음 → 상시 제보만 1.2). `ALWAYS`는 `VALIDATION`.
- 목적지는 정문(`ansimheung.area.gate-lat/lng`)에서 가장 가까운 그래프 노드. `path`는 출발지를 스냅한 노드에서 시작해 정문 노드에서 끝남 (출발지 원래 좌표는 응답·로그에 없음, P-12).
- `distanceM` 정수 반올림, `minutes` = distanceM ÷ 60 반올림, `riskExposure` = 지나는 구간 edgeRisk 합 (소수 첫째 자리), `hazardIds` = 지나는 순서.

출발지 좌표는 저장·로그 금지 (P-12). `timeSlot`이 없으면 서버 현재 시각 기준 `TimeSlot.of()`.

### 관리자 (`/api/admin/**` = ADMIN)
| Method | 경로 | 요청 | 응답 (mock) | 이슈 |
|---|---|---|---|---|
| GET | `/api/admin/hazards` | `?status=&hidden=&sort=OLDEST\|SCORE&page=&size=` | `admin-hazards.json` 페이지 + `counts` | #14 |
| GET | `/api/admin/hazards/{id}` | — | `admin-hazard-detail.json` (전체 제보·작성자 닉네임·미승인 사진 포함·`allowedNextStatuses`) | #14 |
| PATCH | `/api/admin/hazards/{id}/status` | `{status, memo?}` | `{id, status}` / 허용 안 된 전이 409 | #14 |
| PATCH | `/api/admin/hazards/{id}/hidden` | `{hidden, reason}` | `{id, hidden}` | 범위 제외 |
| PATCH | `/api/admin/photos/{id}` | `{approved}` | `{id, approved}` | 범위 제외 |
| GET | `/api/admin/export` | `?format=geojson\|csv` | 파일 | 범위 제외 |

### 알림 · 공공데이터 (범위 제외)
| Method | 경로 | 응답 (mock) |
|---|---|---|
| GET | `/api/me/notifications` | `me-notifications.json` `{unreadCount, items[]}` |
| PATCH | `/api/me/notifications/{id}/read` | 204 |
| GET | `/api/public-layers?kind=` | `public-layers.json` GeoJSON |
