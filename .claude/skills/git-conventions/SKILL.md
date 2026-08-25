---
name: git-conventions
description: 커밋, 브랜치, PR 작업 시 항상 참조. 커밋 메시지 형식(제목 + 불릿), 문서와 코드를 나누는 기준, 어떤 문서가 어느 브랜치에 속하는지, PR용 히스토리를 어떻게 구성하는지를 담음. "커밋해줘", "PR 올려줘", "브랜치 만들어줘" 같은 요청에 적용.
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

## PR용 히스토리 구성

**시행착오를 그대로 올리지 않는다.** 작업 브랜치에는 되돌린 커밋, 뒤늦게 메운 구멍,
문서와 코드가 섞인 커밋이 남기 마련이다.

`main`에서 새 브랜치를 만들고 **최종 결과물을 개발 순서로 다시 커밋한다.**

```bash
git checkout -b feature04 main

# 단계별로 최종 내용을 가져와 담는다
git checkout <작업브랜치> -- <경로들>
./gradlew :apps:commerce-api:test    # 커밋 전마다
git commit -m "..."
```

체리픽보다 이 방식이 낫다 — 중간 상태가 아니라 최종 내용이 단계별로 나뉘어 담기므로
각 커밋이 온전하고 테스트도 통과한다.

### 완료 후 검증

```bash
# 코드가 작업 브랜치와 완전히 일치하는가 (비어 있어야 함)
git diff <새브랜치> <작업브랜치> -- apps/*/src/

# 의도한 파일만 들어갔는가
git diff --name-only main <새브랜치>
```

작업 브랜치는 지우지 않고 남겨둔다. 되돌릴 필요가 생길 수 있다.

## 하지 않는 것

- **push와 PR 생성은 사용자가 명시적으로 지시할 때만.** 커밋까지만 하고 멈춘다
- `main`에 직접 커밋하지 않는다
- 이미 push된 커밋을 `rebase` / `amend` 하지 않는다 (push 전이면 자유롭게 해도 된다)
