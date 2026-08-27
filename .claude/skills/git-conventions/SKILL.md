---
name: git-conventions
description: 커밋, 브랜치, PR 작업 시 항상 참조. 커밋 메시지 형식(제목 + 불릿), 문서와 코드를 나누는 기준, 어떤 문서가 어느 브랜치에 속하는지, push/PR 규칙을 담음. "커밋해줘", "브랜치 만들어줘", "PR 올려줘" 같은 요청에 적용. 기능을 마치고 featureN을 다시 구성하는 절차는 pr-history 스킬에 있다.
---

# Git 컨벤션

## 커밋 메시지

**제목 → 빈 줄 → `-` 불릿.** 본문에 산문 단락을 쓰지 않는다.

```
feat: [좋아요] ProductLike 엔티티와 Product 좋아요 수 증감 추가

- ProductLike 엔티티: (member_id, product_id) UNIQUE 제약 (ProductLike-002)
- likedAt은 BaseEntity.createdAt과 별개로 생성 시점 기록 (공통컨벤션 1.1)
- decreaseLikeCount는 0에서 멈춤 - likeCount는 캐시라 어긋나도 취소 자체는 성공해야 함

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_xxx
```

- 제목 형식: `<type>: [도메인] 요약` — 한글로 쓴다
- `type`: `feat` / `fix` / `refactor` / `test` / `docs` / `chore`
- 배경 설명이 필요해도 문단으로 풀지 말고 불릿 한 줄로 압축한다
- 결정 번호(`ProductLike-006` 등)가 있으면 불릿에 함께 적는다
- 트레일러 `Co-Authored-By` / `Claude-Session` 유지

## 커밋 분리

### 문서와 코드는 항상 별도 커밋

한 커밋에 섞지 않는다. 리뷰 대상이 다르다.

### 기능은 TFD 단계 순서로 나눈다

`tfd-workflow`의 단계와 일치시킨다.

```
1-A 엔티티/값객체  →  1-B 도메인 서비스  →  2 인프라  →  3 application  →  4 interface
```

### Repository 인터페이스와 구현체는 같은 커밋에

`domain`의 Repository 인터페이스만 커밋하면 **구현 빈이 없어 `@SpringBootTest` 계열이 전부
컨텍스트 로딩에 실패한다.** 1-B와 2단계를 한 커밋으로 합치는 것이 맞다.

### 커밋마다 테스트가 통과해야 한다

중간 커밋이 깨져 있으면 리뷰가 무의미하고 `git bisect`도 못 쓴다.

```bash
./gradlew :apps:commerce-api:test
```

## 브랜치

| 브랜치 | 용도 | push |
|---|---|---|
| `main` | 통합. **직접 커밋하지 않는다** — PR 머지로만 갱신 | — |
| `featureN_work` | 실제 작업용. 시행착오가 그대로 쌓인다 | **하지 않는다** (로컬 전용) |
| `featureN` | PR용. `main`에서 새로 만들어 정리된 히스토리만 담는다 | 여기서만 |
| `claude_docs` | 문서 전용. 기능과 무관하게 누적 | |

`N`은 두 자리 (`feature01`, `feature02`, ...). PR은 `featureN`에서 `main`으로 연다.

**왜 `_work`와 `featureN`을 나누는가** — 작업 중에는 되돌리고 다시 고치는 커밋이 생기기 마련이다.
그걸 그대로 PR에 올리면 리뷰어가 최종 결론에 이르기까지의 경로를 전부 읽어야 한다.
`_work`에서는 자유롭게 작업하고, PR은 `featureN`에 다시 구성한다.

`_work`는 PR 후에도 **지우지 않는다.** 되돌리거나 다시 참조할 일이 생긴다.

**기능이 끝나 `featureN`을 구성할 때는 `pr-history` 스킬을 따른다.** `featureN`과 `claude_docs`
양쪽을 담고 검증하는 절차가 거기에 있다. 한쪽만 하고 멈추면 절차가 끝난 것이 아니다.

## 브랜치별 문서 소유

| 대상 | 브랜치 | 이유 |
|---|---|---|
| `.claude/**` | `claude_docs` | 도구 설정·규칙. 구현과 무관 |
| `apps/commerce-api/CLAUDE.md` | `claude_docs` | 위와 같음 |
| `apps/commerce-api/docs/**` | `claude_docs` | 컨벤션 문서 |
| `docs/참고자료/**` | `claude_docs` | 학습·참고용. 특정 기능에 매이지 않음 |
| **`docs/도메인모델/**`** | **구현 브랜치** | 명세는 구현과 **같이 리뷰돼야** 한다 |
| `apps/**/src/**` | 구현 브랜치 | |

도메인 명세만 구현 브랜치로 가는 이유: 명세와 코드가 어긋나면 곧바로 문제가 되므로,
리뷰어가 둘을 나란히 봐야 한다.

### 작업 중에는 전부 `_work`에 커밋한다

위 표는 **최종적으로 어느 브랜치에 남는지**를 정한 것이지, 커밋할 때마다 브랜치를 옮기라는 뜻이 아니다.

작업 도중 스킬이나 컨벤션 문서를 고치는 일은 흔하다. 그때마다 `claude_docs`로 checkout →
커밋 → 되돌아오기 → 머지를 하면 **작업 흐름이 끊기고 머지 커밋만 늘어난다.**
`.claude/**` 변경도 그냥 `_work`에 커밋하고, **`featureN`을 만들 때 분류한다.**

- `_work`는 push하지 않는 로컬 브랜치라 히스토리가 섞여도 비용이 없다
- 어차피 `featureN`은 경로를 골라 담는 방식으로 구성하므로, 분류는 그때 한 번에 처리된다
- 스킬을 고친 즉시 그 브랜치에서 쓸 수 있다. `claude_docs`에만 커밋하면 정작 작업 중인
  브랜치의 워킹트리에는 없어서 적용되지 않는다

## 하지 않는 것

- **push와 PR 생성은 사용자가 명시적으로 지시할 때만.** 커밋까지만 하고 멈춘다
- `main`에 직접 커밋하지 않는다
- 이미 push된 커밋을 `rebase` / `amend` 하지 않는다 (push 전이면 자유롭게 해도 된다)
