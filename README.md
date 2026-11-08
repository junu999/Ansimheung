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

브라우저에서 <http://localhost:8080> → 정왕초 지도(반경 500m 원) 위에 **"✅ 서버 · DB · PostGIS 3.4 연결 완료"** 가 보이면 끝.

```bash
./gradlew test           # 테스트도 통과해야 함 (DB 켜진 상태에서)
```

### ✅ 세팅 완료 체크리스트

- [ ] `java -version` 이 17 이상
- [ ] `docker compose ps` 에서 `ansimheung-db` 가 `healthy`
- [ ] <http://localhost:8080> 에 지도 + ✅ 문구
- [ ] `./gradlew test` 성공
- [ ] `git switch develop` 후 `feature/test-본인이름` 브랜치로 PR 한 번 올려보기 → CI 초록불

---

## 매일 쓰는 명령

| 하고 싶은 것 | 명령 |
|---|---|
| DB 켜기 / 끄기 | `docker compose up -d` / `docker compose down` |
| DB 초기화 (데이터 전부 삭제) | `docker compose down -v` |
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
│   ├── config/                 SecurityConfig 등 설정
│   ├── common/                 공통 (HealthController, 예외 처리)
│   └── (기능별 패키지)          auth/, hazard/, report/, route/, admin/ ... 만들면서 추가
├── src/main/resources/
│   ├── application.yml         공통 설정
│   ├── application-local.yml   로컬 DB 접속 정보
│   └── static/                 프론트 (index.html, js/api.js = API 호출·CSRF 도우미)
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
| VS Code에서 import 빨간 줄 | 명령 팔레트(Ctrl+Shift+P) → `Java: Clean Java Language Server Workspace` |
| 포트 8080 사용 중 | 이전에 띄운 서버가 살아 있음 → 그 터미널에서 Ctrl+C |
| 프론트에서 POST가 `403` | CSRF 토큰 누락 → `fetch` 대신 `static/js/api.js`의 `api.post()` 사용 |

협업 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md).
