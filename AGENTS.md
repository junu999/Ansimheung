# AGENTS.md — AI 코딩 도구용 규칙 (사람도 읽기)

> Claude Code · Cursor · Codex · Copilot 등 AI 도구가 이 저장소에서 코드를 쓸 때 **반드시 먼저 읽는 파일**.
> 팀원은 AI에게 일을 맡기기 전에 이 파일이 컨텍스트에 들어가 있는지 확인하세요. 사람용 상세 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md).

## 1. 프로젝트 한 줄 요약

**안심흥** — 시흥시 **정왕초등학교 반경 500m** 통학로의 위험 제보를 모아 위험점수로 바꾸고, 최단·균형·안전 3가지 도보 경로를 비교해 주는 웹서비스 (웹서비스프로그래밍 8팀 S-Link, **최종 제출 2026-11-29**).

**시연은 로컬에서만 한다** (팀원 노트북 `docker compose` + `bootRun`). 배포·도메인·HTTPS·운영 서버 설정은 만들지 않는다.

흐름: 주민 제보 → 위험점수 계산 → 경로 추천에 반영 → 관리자 처리(개선완료) → 점수·경로 재반영.

## 2. 무엇이 정답인가 (충돌 시 위가 이김)

1. **코드에 이미 있는 것** — `common/type/*` enum, `application.yml` `ansimheung.*`, `db/migration/V*.sql`, `common/error/ErrorCode`
2. **이 저장소 문서** — [docs/domain.md](docs/domain.md)(정책·상수·검증·알고리즘), [docs/api.md](docs/api.md)(API 계약), [docs/erd.md](docs/erd.md), `src/main/resources/static/mock/*.json`(응답 예시)
3. 노션(사람만 접근 가능) — 위 문서의 원본. 문서와 노션이 다르면 **추측하지 말고 PR 설명에 적어 사람에게 물어볼 것**

GitHub 이슈(#8~#28)에 작업 단위·완료 기준이 있다. 작업 전에 해당 이슈 본문을 읽는다.

## 3. 명령어

```bash
docker compose up -d          # PostgreSQL 16 + PostGIS 3.4 (테스트에도 필요)
./gradlew bootRun             # 서버 http://localhost:8080  (화면 /, 연결 확인 /dev.html, 가짜 데이터 /?mock)
./gradlew test                # 백엔드 테스트 (DB 켜진 상태에서)
./gradlew build               # CI와 같은 검사
node --test src/test/js/mappers.test.mjs   # 프론트 변환 테스트 (Node 20+)
docker compose down -v        # DB 초기화 (Flyway 오류 시)
```

PR 올리기 전 `./gradlew test`가 통과해야 한다. 통과를 확인하지 못했으면 PR 설명에 그렇게 쓴다.

## 4. 구조

```
src/main/java/com/slink/ansimheung/
├── common/            이미 있음 — error/(ErrorCode·ApiException), type/(enum), geo/(PilotArea·GeoUtils), ConfigController
├── config/            이미 있음 — AnsimheungProperties(ansimheung.*), SecurityConfig(세션+CSRF 쿠키)
├── auth/              User · LoginUser · LoginUserDetailsService (#31) + 회원가입·로그인 API (#9)
├── hazard/            Hazard·Report·Photo·Empathy·StatusHistory 엔티티·리포지토리 (#31) + 제보·조회·공감 API (#11 #12 #24~#26)
├── risk/              RiskService 계약 (#31) → 실제 구현 RiskCalculator·구간 배정 (#10)
├── route/             Node·Edge 엔티티 (#31) + Graph·Dijkstra·RouteService·POST /api/routes (#8 #13)
├── admin/             /api/admin/** (#14)
├── notification/ · publiclayer/   엔티티만 (기능은 범위 제외 #28)
└── seed/              시연 계정(관리자·주민) + 시드 30건 — 오직 여기서만 (#15)
src/main/resources/
├── application.yml    정책값 ansimheung.* (하드코딩 금지 — AnsimheungProperties로 읽기)
├── db/migration/      Flyway. V1 = 10개 테이블. 변경은 V2__설명.sql 새 파일
└── static/            프론트 (Spring이 같은 출처로 서빙 → CORS 없음)
    ├── index.html · css/ · vendor/leaflet/
    ├── js/app.js      화면 흐름          js/map.js    Leaflet
    ├── js/api.js      화면용 API 어댑터  js/mappers.js 서버 ↔ 화면 변환 (순수 함수)
    ├── js/http.js     fetch·CSRF·오류·?mock          js/config.js /api/config 로드
    └── mock/*.json    API 응답 예시 = 계약
```

기능 패키지 안은 `XxxController` · `XxxService` · `XxxRepository` · `dto/`(Java `record`) · 엔티티. 패키지를 새로 만들기 전에 위 목록에 맞는지 본다.

### 이미 정해진 모듈 계약 (#31) — 새로 만들지 말고 이것을 쓴다
| 필요한 것 | 쓰는 것 |
|---|---|
| 엔티티 | 위 엔티티 그대로. **연관관계(`@ManyToOne`) 없이 외래키 id 필드**(`report.getHazardId()`). 새 엔티티·필드가 필요하면 V2 마이그레이션과 함께 |
| 엔티티 만들기·바꾸기 | 정적 생성 메서드(`Hazard.create(...)`, `Report.create(...)`)와 도메인 메서드(`hazard.changeStatus()`, `addReport()`, `addEmpathy()`, `hide()`). setter 추가 금지 |
| 좌표 | `Geometries.point(lat, lng)` · `hazard.getLat()/getLng()` (JTS 내부는 x=경도) |
| 로그인 사용자 | 컨트롤러 파라미터 `@AuthenticationPrincipal LoginUser user` → `user.id()`, `user.isAdmin()` |
| 위험점수 | `RiskService.refresh(hazard)` (제보·공감·상태 변경 직후 같은 트랜잭션), `scoreAt(hazard, slot)`, `levelOf(hazard, score)` |
| 권한 | `SecurityConfig`의 규칙. 새 회원 전용 API는 거기 한 줄 + `SecurityConfigTest` 한 줄 |

## 5. 반드시 지킬 것 (MUST)

### 공통
- **이슈 하나 = 브랜치 하나 = PR 하나.** 브랜치 `feature/{기능ID}-설명`, base는 `develop`. PR 300줄 넘으면 쪼갠다.
- 이슈 범위 밖 파일은 고치지 않는다. 고쳐야 할 것을 발견하면 PR 설명의 "따로 처리할 것"에 적는다.
- 커밋 메시지 `타입(범위): 한글 설명` — `feat` `fix` `refactor` `test` `docs` `chore`.
- 이름은 용어 사전 영문명: `Hazard`(위험 지점) `Report`(제보) `Empathy`(공감) `riskScore` `baseScore` `Node` `Edge` `edgeRisk` `cost` `lambda` `status` `seed`.

### 백엔드
- 오류는 `throw new ApiException(ErrorCode.XXX)`만. 새 코드가 필요하면 `ErrorCode`에 추가하고 [docs/api.md](docs/api.md) 오류 표도 같이 수정.
- 숫자 정책값(20m, 25m, 50m, 500m, λ, 한도, 가중치)은 `AnsimheungProperties`·enum에서만 읽는다. 매직 넘버 금지.
- 시범 지역 검사는 `PilotArea.requireInside(lat, lng)`.
- enum은 `common/type/`의 것만 쓴다 (`HazardType` `TimeSlot` `HazardStatus` `MarkerLevel` `RouteMode` `Role`). 상태 전이는 `HazardStatus.canTransitionTo()`.
- 요청 DTO에 `@Valid` + Bean Validation, 검증 메시지는 docs/domain.md V-표의 문구 그대로.
- 응답 JSON은 camelCase, 시간은 `OffsetDateTime`(+09:00), 점수는 소수 첫째 자리 반올림, 빈 값은 필드 생략 대신 `null`.
- **응답 모양은 `static/mock/*.json`과 같아야 한다.** 바꿔야 하면 mock JSON·`js/mappers.js`·docs/api.md를 같은 PR에서 함께 고친다.
- DB 변경은 `V{다음번호}__설명.sql` 새 파일 + 엔티티 수정을 같은 PR에 (`ddl-auto: validate`).
- 위험점수: DB `hazards.base_score = W×R×E×S`, 시간대 T는 조회할 때 곱한다. 제보·공감·상태 변경 직후 `RiskCalculator`로 재계산.
- 서비스 로직에는 테스트를 붙인다. docs/domain.md의 계산 예시(4.0 / 6.0 / 1.8)와 A/B 경로 예시가 그대로 테스트 기대값이다.

### 테스트 방식 (팀 합의 11/24)
| 대상 | 방식 | 예 |
|---|---|---|
| 위험점수 계산(`RiskCalculator`), Dijkstra·`Graph`, 엔티티 도메인 메서드, 순수 변환 | **순수 단위 테스트** — `@SpringBootTest` 없이 `new`로 만들어 JUnit만. DB·Spring 컨텍스트 ❌ | `HazardTest`, `RiskCalculatorTest`, `DijkstraTest` |
| API(컨트롤러~DB), 리포지토리 쿼리, 권한 | **`@SpringBootTest` + `@Transactional`** (+ `@AutoConfigureMockMvc`) — 테스트 끝나면 롤백. DB 필요 | `EntityMappingTest`, `SecurityConfigTest` |
- 순수 단위 테스트에 필요한 정책값은 `new AnsimheungProperties(...)` 대신 테스트 안에서 값을 직접 넘길 수 있게 계산 클래스를 설계한다 (생성자에 숫자·record 주입).
- 테스트 데이터는 각 테스트가 직접 만든다. 시드 데이터(#15)에 기대지 않는다.
- 로그인 사용자가 필요한 API 테스트는 `SecurityMockMvcRequestPostProcessors.user(...)` 또는 테스트 안에서 `User` 저장 후 로그인.

### 프론트
- 서버 호출은 `js/api.js`의 메서드로만. 새 API는 `api.js` 메서드 → `mappers.js` 변환 → `mock/`에 JSON → `http.js` `MOCK_ROUTES` 한 줄, 그리고 `src/test/js/mappers.test.mjs`에 테스트.
- `fetch` 직접 호출 금지 (CSRF 403 남). 학교 좌표·반경 하드코딩 금지 (`config.school`).
- 사용자 입력·서버 문자열은 `esc()`(escapeHtml)나 `textContent`로만 출력 (XSS).
- 오류는 `error.message`를 그대로 보여준다 (서버가 사용자용 한국어 문장을 줌).

## 6. 하지 말 것 (MUST NOT)

- 이미 머지된 `V1__init_schema.sql` 등 마이그레이션 수정
- `main`·`develop`에 직접 push, 강제 push, 보호 규칙 우회
- 비밀값·`.env`·`application-prod.yml`·`uploads/` 커밋. 실제 개인정보(이메일·전화·얼굴 사진)를 테스트 데이터·프롬프트에 넣기
- **시연 계정(관리자·주민)이나 시드 데이터를 #15(`seed/`) 밖에서 만들기** — 다른 기능 PR에 `CommandLineRunner`·`data.sql`·초기 계정 코드 ❌
- 배포용 설정(Dockerfile, 운영 프로필, 클라우드 설정) 추가 — 시연은 로컬만
- **경로 출발지 좌표를 DB·로그에 남기기** (P-12). 사진은 EXIF 제거 전 저장 금지 (P-08). 위험 지점 상세에 작성자 노출 금지(관리자 화면만)
- 새 라이브러리·프레임워크 추가 (npm, React, Lombok, QueryDSL 등) — 필요하면 PR에서 먼저 제안
- 테스트를 지우거나 `@Disabled`로 꺼서 통과시키기, 실패를 숨기는 `catch (Exception e) {}`
- 노션·기획서에 없는 기능을 "있으면 좋을 것 같아서" 추가 (범위 제외 목록은 이슈 #28)
- 측정하지 않은 숫자를 발표자료·README·주석에 쓰기

## 7. AI가 자주 틀리는 곳

| 함정 | 정답 |
|---|---|
| 좌표 순서 | JSON 필드 `lat`/`lng` · GeoJSON `[lng, lat]` · Leaflet `[lat, lng]` · bbox `서,남,동,북` |
| enum 값 | `ILLEGAL_PARKING`(⭕) `parking`(❌) — 화면 키 ↔ enum 변환은 `mappers.js`에서만 |
| 경로 요청 | `POST /api/routes {fromLat, fromLng, timeSlot?}` (GET·쿼리스트링 ❌ — 출발지가 URL에 남음) |
| 시간대 | 등교 07–09, 하교 **14–18**, 야간 18–07, 09–14시는 해당 없음 → `TimeSlot.of()` 사용 |
| 구간 위험도 | 위험 지점은 **가장 가까운 구간 1개**(25m 이내)에만 배정. 반경 합산 ❌ (교차로 중복 계산) |
| 상태 전이 | 접수→확인중/반려, 확인중→개선완료/반려. 접수→개선완료 바로 ❌ (409 `STATE_CONFLICT`) |
| 로그인 | JSON `{email,password}` + 세션 쿠키. Spring Security 폼 로그인·JWT ❌ |
| 사진 경로 | `GET /api/photos/{id}`로만 제공 (권한 확인). `static/`에 저장 ❌ |
| 경로 캐시 | 요청마다 Dijkstra 3회. 사전 계산·캐시 ❌ (그래프 200노드, 1ms 미만) |
| 시범 지역 | **정왕초**(37.3408, 126.7430). 옛 문서의 "신일초"는 폐기된 값 |

## 8. 확실하지 않을 때

- 명세에 없는 동작은 만들지 말고 `// TODO(#이슈번호): 질문 내용`을 남기고 PR 설명 "확인 필요"에 적는다.
- 여러 해석이 가능하면 가장 단순한 쪽을 고르고 그 선택을 PR 설명에 적는다.
- 테스트가 DB 없이 안 돌아가면 그렇다고 보고한다. 통과했다고 추측해서 쓰지 않는다.

## 9. 완료 기준 (Definition of Done)

- [ ] 이슈의 "완료 기준"을 모두 만족 (PR에 `Closes #번호`)
- [ ] `./gradlew test` 통과 (프론트 변환을 바꿨으면 node 테스트도)
- [ ] 응답이 mock JSON과 같은 모양, 오류는 ErrorCode
- [ ] 정책값 하드코딩 없음, 새 의존성 없음
- [ ] PR 템플릿의 "AI 사용" 칸 작성 — 사람이 직접 읽고 이해한 코드만 올린다
