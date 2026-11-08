# 안심흥 (Ansimheung)

> 가장 빠른 통학길이 아니라, 더 안전한 통학길을 찾습니다.

학부모가 "집 → 학교" 길을 고를 때 위험 제보가 적은 길을 추천하는 웹서비스 · 팀 S-Link

| 구분 | 기술 |
|---|---|
| Frontend | HTML / CSS / JavaScript, Leaflet + OpenStreetMap (`src/main/resources/static/`) |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Database | PostgreSQL 16 + PostGIS 3.4 (Docker) |
| 협업 | GitHub (PR + CI), Notion, VS Code |

---

## 개발 환경 세팅 (처음 한 번)

### 1. 설치할 것

| 프로그램 | 확인 명령 | 비고 |
|---|---|---|
| Git | `git --version` | |
| JDK 21 | `java -version` → 21 | [Temurin 21](https://adoptium.net) 권장 |
| Docker Desktop | `docker --version` | 설치 후 **실행해 둔 상태**여야 함 |
| VS Code | | 폴더를 열면 추천 확장 설치 안내가 뜸 → **모두 설치** |

> Gradle과 PostgreSQL은 따로 설치하지 않습니다. Gradle은 `gradlew`가, DB는 Docker가 알아서 받아옵니다.

### 2. 받기

```bash
git clone https://github.com/<조직>/Ansimheung.git
cd Ansimheung
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

### 5. 확인

브라우저에서 <http://localhost:8080> → 지도 위에 **"✅ 서버 · DB · PostGIS 3.4 연결 완료"** 가 보이면 끝.

```bash
./gradlew test           # 테스트도 통과해야 함 (DB 켜진 상태에서)
```

### ✅ 세팅 완료 체크리스트

- [ ] `java -version` 이 21
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
│   └── static/                 프론트 (index.html, js/, css/)
└── .github/                    CI, PR · 이슈 템플릿
```

## 자주 막히는 곳

| 증상 | 해결 |
|---|---|
| `Connection refused ... 5432` | Docker Desktop이 꺼져 있음 → 켜고 `docker compose up -d` |
| `port 5432 already in use` | PC에 PostgreSQL이 따로 설치돼 실행 중 → 그 서비스를 끄거나, `docker-compose.yml` 포트를 `"5433:5432"`로 바꾸고 `application-local.yml` URL도 5433으로 |
| `./gradlew: Permission denied` (맥) | `chmod +x gradlew` |
| VS Code에서 import 빨간 줄 | 명령 팔레트(Ctrl+Shift+P) → `Java: Clean Java Language Server Workspace` |
| 포트 8080 사용 중 | 이전에 띄운 서버가 살아 있음 → 터미널에서 Ctrl+C |

협업 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md).
