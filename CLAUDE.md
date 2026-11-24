# CLAUDE.md

이 저장소의 규칙은 모든 AI 도구가 같이 쓰도록 AGENTS.md 한 곳에 있습니다. 아래 줄이 그 파일을 불러옵니다.

@AGENTS.md

## Claude Code 전용 메모

- 작업 시작 전에 해당 GitHub 이슈 본문(완료 기준)과 [docs/domain.md](docs/domain.md)·[docs/api.md](docs/api.md)의 관련 절을 읽는다.
- 큰 작업은 먼저 계획(만들 파일·테스트 목록)을 보여주고 사람이 확인한 뒤 코드를 쓴다.
- `./gradlew test`는 `docker compose up -d`가 되어 있어야 통과한다. 실행 못 했으면 "실행 못 함"이라고 보고한다.
- `git push`·PR 생성·이슈 수정은 사람이 요청했을 때만 한다.
