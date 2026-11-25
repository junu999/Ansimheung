# 안심흥 (Ansimheung)

> 가장 빠른 통학길이 아니라, 더 안전한 통학길을 찾습니다.

학부모가 "집 → 학교" 길을 고를 때 위험 제보가 적은 길을 추천하는 웹서비스 · 팀 S-Link

시범 지역: 경기 시흥시 **정왕초등학교**(중심상가로 257) 반경 500m

| 구분 | 기술 |
|---|---|
| Frontend | HTML / CSS / JavaScript, Leaflet + OpenStreetMap (`src/main/resources/static/`) |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Database | PostgreSQL 16 + PostGIS 3.4 (로컬은 Docker로 실행) |
| 협업 | GitHub (PR + CI), Notion, VS Code |

---

## 개발 환경 세팅 (처음 한 번)

### 1. 설치할 것

| 프로그램 | 확인 명령 | 비고 |
|---|---|---|
| Git | `git --version` | |
| JDK 17 이상 | `java -version` | 21·25 등 아무 버전이나 OK. 컴파일용 Java 21은 Gradle이 자동으로 받아요 |
| Docker Desktop | `docker --version` | 설치 후 **실행해 둔 상태**여야 함 |
| VS Code | | 폴더를 열면 추천 확장 설치 안내가 뜸 → **모두 설치** |

> Gradle과 PostgreSQL은 따로 설치하지 않습니다. Gradle은 `gradlew`가, DB는 Docker가 알아서 받아옵니다.
> Docker는 DB를 바꾸는 게 아니라 **PostgreSQL + PostGIS를 설치 없이 똑같은 설정으로 띄워주는 도구**예요. DB 데이터는 노트북마다 따로 저장됩니다.

### 2. 받기

```bash
git clone https://github.com/junu999/Ansimheung.git
cd Ansimheung
git switch develop
code .
```

### 3. DB 켜기

```bash
docker compose up -d
```

### 4. 서버 켜기

```bash
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

또는 VS Code 왼쪽 **실행 및 디버그(Ctrl+Shift+D)** → "안심흥 서버 실행 (local)" ▶

- 첫 실행은 Gradle·Java 21·라이브러리를 받느라 **3~5분** 걸려요. 두 번째부터는 몇 초.
- 로그에 `Started AnsimheungApplication`이 나오면 켜진 것. 터미널이 `80% EXECUTING`에 머무는 건 **정상**(서버 실행 중). 끌 때는 Ctrl+C.

### 5. 확인

브라우저에서 <http://localhost:8080/dev.html> → 정왕초 지도(반경 500m 원) 위에 **"✅ 서버 · DB · PostGIS 3.4 연결 완료"** 가 보이면 끝.
서비스 화면은 <http://localhost:8080> (백엔드 API가 없을 때는 <http://localhost:8080/?mock>).

```bash
./gradlew test           # 테스트도 통과해야 함 (DB 켜진 상태에서)
```

### ✅ 세팅 완료 체크리스트

- [ ] `java -version` 이 17 이상
- [ ] `docker compose ps` 에서 `ansimheung-db` 가 `healthy`
- [ ] <http://localhost:8080/dev.html> 에 지도 + ✅ 문구
- [ ] `./gradlew test` 성공
- [ ] `git switch develop` 후 `feature/test-본인이름` 브랜치로 PR 한 번 올려보기 → CI 초록불

### 시연 계정 · 시연용 데이터

`./gradlew bootRun`(local 프로필)으로 처음 켜면 시연용 데이터가 자동으로 들어갑니다. 이미 있으면 건너뜁니다.

| 계정 | 이메일 | 비밀번호 |
|---|---|---|
| 관리자 | `admin@ansimheung.local` | `ansim1234` |
| 주민(학부모) | `parent@ansimheung.local` | `ansim1234` |

- **팀 전체가 이 두 계정만 씁니다** (11/24 합의). 다른 PR에서 계정을 따로 만들지 않아요. 비밀번호는 `application-local.yml`의 `ansimheung.seed.*`.
- 위험 지점 30곳 = 시연용 예시 데이터 (실제 제보 아님). 화면에는 `GET /api/config`의 `demoData: true`로 DEMO 표시.
- 시연 출발지 "북쪽 골목 입구"(37.344404, 126.743579) → 최단 619m·10분 / 균형 813m·14분 / 안전 915m·15분.
- 처음 상태로 되돌리기: `docker compose down -v` → `docker compose up -d` → `./gradlew bootRun`.

---

## 매일 쓰는 명령

| 하고 싶은 것 | 명령 |
|---|---|
| DB 켜기 / 끄기 | `docker compose up -d` / `docker compose down` |
| DB 초기화 (데이터 전부 삭제) | `docker compose down -v` (다음 서버 실행 때 Flyway 가 테이블을 다시 만듦) |
| DB 직접 들어가기 | `docker exec -it ansimheung-db psql -U ansimheung` |
| 서버 실행 | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |

## 폴더 구조

```
├── docker-compose.yml          로컬 PostGIS
├── docker/init/                DB 최초 생성 시 실행할 SQL
├── http/                       API 호출 테스트 (.http, REST Client 확장)
├── src/main/java/com/slink/ansimheung/
│   ├── AnsimheungApplication.java
│   ├── config/                 SecurityConfig, AnsimheungProperties(정책값)
│   ├── common/                 HealthController, ConfigController(/api/config)
│   │   ├── error/              ErrorCode, ApiException, 공통 오류 응답 처리
│   │   ├── type/               공통 enum (HazardType, TimeSlot, HazardStatus …)
│   │   └── geo/                PilotArea(시범 지역 검사), GeoUtils
│   └── (기능별 패키지)          auth/, hazard/, report/, route/, admin/ ... 만들면서 추가
├── src/main/resources/
│   ├── db/migration/           Flyway DB 스키마 (V1__init_schema.sql …)
│   ├── application.yml         공통 설정 + 서비스 정책값(ansimheung.*)
│   ├── application-local.yml   로컬 DB 접속 정보
│   └── static/                 프론트 (index.html = 서비스 화면, dev.html = 연결 확인, js/, mock/ = 가짜 응답)
└── .github/                    CI, PR · 이슈 템플릿
```

## 자주 막히는 곳

| 증상 | 원인 → 해결 |
|---|---|
| `Unsupported class file major version 69` | PC 기본 Java가 25인데 옛 Gradle(8.x) → `develop` 최신으로 `git pull` (Gradle 9.2.1) |
| Gradle 다운로드 중 `HTTP 504` / `timeout` | Gradle 배포 서버 일시 장애 → 1~2분 뒤 다시 실행 |
| VS Code에 "Choose Active Connection 127.0.0.1@3306" 창 | VS Code DB 확장이 `.sql` 파일을 보고 묻는 것(다른 프로젝트 MySQL) → Esc, 무시. 서버는 5432로 따로 연결 |
| `Connection refused ... 5432` | Docker Desktop 꺼짐 → 켜고 `docker compose up -d` |
| `port 5432 already in use` | 다른 프로젝트 Postgres(컨테이너 또는 설치본)가 실행 중 → 그걸 끄거나, `docker-compose.yml` 포트를 `"5433:5432"`로 바꾸고 `application-local.yml` URL도 5433으로 |
| `./gradlew: Permission denied` (맥) | `chmod +x gradlew` |
| `Validate failed: Migrations have failed validation` | 머지된 마이그레이션 파일을 고쳤음 → 되돌리고 V2 로 추가. 로컬만 꼬였으면 `docker compose down -v` |
| `Schema-validation: missing table/column` | 엔티티에 맞는 마이그레이션(V2…)을 안 만들었음 (`ddl-auto: validate`) |
| VS Code에서 import 빨간 줄 | 명령 팔레트(Ctrl+Shift+P) → `Java: Clean Java Language Server Workspace` |
| 포트 8080 사용 중 | 이전에 띄운 서버가 살아 있음 → 그 터미널에서 Ctrl+C |
| 프론트에서 POST가 `403` | CSRF 토큰 누락 → `fetch` 직접 쓰지 말고 `static/js/http.js`(또는 `api.js`) 사용 |

## 개발 규칙 요약

### 백엔드
| 하고 싶은 것 | 방법 |
|---|---|
| 오류 응답 보내기 | `throw new ApiException(ErrorCode.LIMIT, "오늘은 더 제보할 수 없어요 (하루 10건)")` → `{code, message, fields}` 자동 변환 (`common/error/`) |
| 입력 검증 | DTO 에 `@NotBlank`·`@Size` + 컨트롤러 `@Valid` → 실패 시 `VALIDATION` + 칸별 메시지 자동 |
| 정책값 쓰기 (20m, λ, 한도…) | 하드코딩 ❌ → `AnsimheungProperties` 주입 (`application.yml` 의 `ansimheung.*`, 노션 "상수(C)·검증(V)" 표와 같음) |
| 시범 지역 검사 | `pilotArea.requireInside(lat, lng)` → 밖이면 `OUT_OF_AREA` |
| enum | `common/type/` 의 `HazardType`·`TimeSlot`·`HazardStatus`·`MarkerLevel`·`RouteMode`·`Role` 만 사용 |
| DB 테이블 추가·변경 | `src/main/resources/db/migration/V2__설명.sql` 새 파일 추가 (이미 머지된 V1 수정 ❌). 그림은 [docs/erd.md](docs/erd.md) |

### 프론트
ES 모듈 + Leaflet(`static/vendor/leaflet`, 설치·CDN 불필요). Spring이 `static/`을 그대로 서빙 → 같은 출처라 CORS 설정 없음.

| 파일 | 역할 |
|---|---|
| `index.html` · `css/style.css` | 화면 구조·스타일 (데스크톱·모바일) |
| `js/app.js` | 화면 흐름 (지도·제보·상세·경로·알림·관리자) |
| `js/map.js` | Leaflet 지도·마커·경로 선 |
| `js/api.js` | **화면용 API 어댑터** — app.js 는 이것만 호출 |
| `js/mappers.js` | 서버 응답(노션 API 명세 · `mock/*.json`) ↔ 화면 모델 변환. 필드명이 바뀌면 여기만 수정 |
| `js/http.js` | fetch 래퍼 — CSRF(`X-XSRF-TOKEN`)·오류(`{code,message,fields}`)·`?mock` 처리 |
| `js/config.js` | `GET /api/config` 로 학교 좌표·반경 로드 (하드코딩 ❌) |
| `js/labels.js` | 유형·시간대·상태 라벨과 아이콘, 경로 화면 예시 출발지 |

| 하고 싶은 것 | 방법 |
|---|---|
| 새 API 호출 추가 | `api.js` 에 메서드 추가 → 응답 변환은 `mappers.js` → `mock/` 에 같은 모양 JSON + `http.js` `MOCK_ROUTES` 한 줄 |
| 오류 표시 | `catch (e) { toast(e.message) }` — `e.status`, `e.code`, `e.fields` 도 있음 |
| 백엔드 없이 화면 작업 | 주소 끝에 **`?mock`** → <http://localhost:8080/?mock> . 로그인 창의 체험 버튼으로 회원/관리자 전환 |
| 변환 테스트 | `node --test src/test/js/mappers.test.mjs` (Node 20+) |
| 위험점수 표시 | 서버 점수(최대 9.0)를 100점 만점으로 환산해 표시, 마커 색은 서버 `level` 그대로 (`mappers.js` `toDisplayScore`) |
| 제보 설명 | 별도 제목 없이 선택 설명 `description`을 최대 200자 입력. 기존 제목+설명 형식의 제보도 상세 조회 가능 |

## 문서

| 문서 | 내용 |
|---|---|
| [CONTRIBUTING.md](CONTRIBUTING.md) | 브랜치·커밋·PR·이슈·충돌 해결·Flyway 규칙 |
| [AGENTS.md](AGENTS.md) | AI 코딩 도구용 규칙 (Claude Code는 [CLAUDE.md](CLAUDE.md), Copilot은 `.github/copilot-instructions.md`가 이것을 가리킴) |
| [docs/ai-workflow.md](docs/ai-workflow.md) | 팀의 AI 사용법 · 프롬프트 템플릿 · 리뷰 체크리스트 |
| [docs/domain.md](docs/domain.md) | 정책(P)·상수(C)·검증(V)·상태 전이·위험도 산정식·경로 알고리즘 + 테스트 기대값 |
| [docs/api.md](docs/api.md) | API 계약 (공통 규칙·오류 코드·enum·엔드포인트) |
| [docs/erd.md](docs/erd.md) | DB 구조 |
