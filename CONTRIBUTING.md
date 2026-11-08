# 협업 규칙

## 브랜치

| 브랜치 | 용도 | 규칙 |
|---|---|---|
| `main` | 시연·제출용 | 직접 push 금지. `develop`에서 주차 마감 때만 머지 |
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
# GitHub에서 develop 으로 PR 생성 → 1명 승인 → Squash and merge
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
- 노션 태스크의 "PR 링크" 칸에 PR 주소 붙이기
- 리뷰어: 백엔드 PR은 선준우·정지훈 서로, 프론트 PR은 정지훈
- 머지 전 `./gradlew test` 통과 확인 (GitHub Actions CI가 자동으로도 돌림)

## 코드 이름

기획서 2장 용어 사전의 영문명을 따릅니다: `Report`, `Hazard`, `Empathy`, `riskScore`, `Node`, `Edge`, `edgeRisk`, `cost`, `lambda`, `status`, `seed`.

## 절대 커밋하지 않는 것

- `application-local.yml` (DB 비밀번호)
- 업로드 사진 (`uploads/`)
