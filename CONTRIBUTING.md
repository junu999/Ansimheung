# 협업 규칙

> AI 도구로 코드를 쓸 때는 [AGENTS.md](AGENTS.md)(AI용 규칙)와 [docs/ai-workflow.md](docs/ai-workflow.md)(팀 사용법·프롬프트·리뷰 체크리스트)를 함께 봅니다.
> 정책·알고리즘은 [docs/domain.md](docs/domain.md), API 계약은 [docs/api.md](docs/api.md).

## 브랜치

| 브랜치 | 용도 | 규칙 |
|---|---|---|
| `main` | 시연·제출용 | 직접 push 금지. `develop` → `main` PR로 주차 마감 때만 머지 |
| `develop` | 통합 | 직접 push 금지. PR로만 머지 |
| `feature/{기능ID}-{설명}` | 기능 개발 | `develop`에서 따서 `develop`으로 PR |
| `fix/{설명}` | 버그 수정 | 위와 같음 |
| `docs/{설명}` | 문서만 수정 | 위와 같음 |

기능 ID는 기획서 6장의 ID를 그대로 씁니다.

```
feature/F-ROUTE-01-dijkstra
feature/F-AUTH-01-signup
feature/F-RPT-01-report-api
feature/F-MAP-01-hazard-map
```

## 작업 순서

```bash
git switch develop
git pull
git switch -c feature/F-ROUTE-01-dijkstra
# ... 작업 ...
git add .
git commit -m "feat(route): Dijkstra 경로 탐색 구현"
git push -u origin feature/F-ROUTE-01-dijkstra
# GitHub에서 base: develop 으로 PR 생성 → 팀원 1명 Approve → CI 통과 → Squash and merge
# 머지되면 원격 브랜치는 자동 삭제 → 로컬 정리:
git switch develop && git pull && git fetch --prune
git branch -D feature/F-ROUTE-01-dijkstra   # Squash merge라 -d 는 "머지 안 됨"으로 거부됨 → -D
```

## 커밋 메시지

`타입(범위): 내용` — 내용은 한글로 짧게.

| 타입 | 언제 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 코드 정리 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 |
| `chore` | 빌드·설정 |

범위 예: `route`, `risk`, `auth`, `report`, `map`, `admin`, `db`

## PR

- PR 하나 = 기능 하나. 300줄 넘으면 쪼개기
- **PR 제목도 커밋 메시지 규칙대로** (`feat(route): Dijkstra 경로 탐색 구현`) — Squash merge하면 PR 제목이 develop에 남는 커밋 메시지가 됨
- PR 설명에 `Closes #이슈번호` (작업 관리는 GitHub 이슈 기준, 11/24~) — 단, 아래 "이슈" 절의 주의 참고
- 리뷰어: 백엔드 PR은 선준우·정지훈 서로, 프론트 PR은 정지훈
- 머지 조건(저장소 규칙으로 강제): PR + 승인 1명 + CI `build` 통과. 본인 PR은 본인이 승인할 수 없음
- 올리기 전에 로컬에서 `./gradlew test` 한 번 돌려보기

## PR 올린 뒤

**리뷰 반영** — 같은 브랜치에 커밋을 더 하고 push하면 PR이 자동으로 갱신됩니다. PR을 새로 만들지 않기.

```bash
git add . && git commit -m "fix(route): 리뷰 반영 — 음수 가중치 검사"
git push
```

**CI가 빨간불(❌)일 때** — PR 아래 Checks의 `build` → **Details** → 빨간 단계의 로그를 펼쳐 첫 번째 오류를 찾습니다. 대부분 테스트 실패라 로컬에서 `./gradlew test`로 재현 → 고쳐서 push하면 CI가 다시 돕니다.

**develop이 앞서가서 충돌(conflict)이 났을 때** — 내 브랜치에 develop을 merge합니다. (rebase + force push는 쓰지 않기)

```bash
git switch feature/F-ROUTE-01-dijkstra
git fetch origin
git merge origin/develop
# 충돌 파일에서 <<<<<<< ======= >>>>>>> 부분을 직접 고친 뒤
git add . && git commit        # 기본 메시지 그대로 저장
git push
```

충돌 난 곳이 남의 코드라 어떻게 합칠지 모르겠으면 혼자 정하지 말고 그 코드 작성자에게 물어보기.

## 이슈

- 작업 목록은 GitHub 이슈 #8~#28. 시작할 때 이슈 오른쪽 **Assignees**에 본인을 지정해 겹치지 않게 합니다.
- 새 작업은 **New issue → "작업" 템플릿**, 제목 `[기능ID] 설명`, 라벨을 붙입니다.

| 라벨 | 뜻 |
|---|---|
| `P0` / `P1` | 시연 필수(11/27까지) / P0 끝난 뒤 |
| `BE` / `ALGO` / `FE` | 담당 파트 |
| `area:*` | 기능 영역 (`area:route`, `area:report` …) |
| `bug` | 버그 |

- ⚠️ **`Closes #번호`는 `develop` 머지로는 이슈를 닫지 않습니다.** GitHub는 기본 브랜치(`main`)에 머지될 때만 자동으로 닫기 때문입니다. develop에 머지되면 **이슈에 PR 링크를 남기고 직접 Close** 합니다.

## DB 변경 (Flyway)

- 테이블·컬럼을 바꾸려면 `src/main/resources/db/migration/` 에 **새 파일** `V{다음번호}__설명.sql` 추가 (예: `V2__add_hazard_title.sql`)
- **이미 머지된 마이그레이션 파일은 절대 수정하지 않기** — 다른 사람 DB와 어긋나서 서버가 안 뜸
- 번호가 겹치면 나중에 머지하는 사람이 번호를 올림
- 엔티티를 바꿨으면 마이그레이션도 같은 PR 에 (`ddl-auto: validate` 라 안 맞으면 서버가 안 뜸)

## 오류·정책값

- 오류는 `throw new ApiException(ErrorCode.XXX)` — 직접 `ResponseEntity.status(...)` 로 오류 본문 만들지 않기
- 새 오류 코드가 필요하면 `ErrorCode` 에 추가하고 `docs/api.md` 오류 표(와 노션 "API 공통 규약")도 같이 수정
- 20m·500m·λ·하루 한도 같은 숫자는 `AnsimheungProperties` 로만 읽기

## 코드 이름

기획서 2장 용어 사전의 영문명을 따릅니다: `Report`, `Hazard`, `Empathy`, `riskScore`, `Node`, `Edge`, `edgeRisk`, `cost`, `lambda`, `status`, `seed`.

## 절대 커밋하지 않는 것

- `application-prod.yml`, `.env` (실제 서버 비밀값) — `application-local.yml`은 로컬 Docker 전용 값이라 커밋되어 있음
- 업로드 사진 (`uploads/`)
