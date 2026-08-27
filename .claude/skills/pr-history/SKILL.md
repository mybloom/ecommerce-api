---
name: pr-history
description: 기능 작업을 마치고 PR용 브랜치를 구성할 때 사용. main에서 featureN을 만들어 최종 결과물을 의존 순서로 다시 커밋하고, _work에 섞인 .claude/** 등은 claude_docs로 옮긴 뒤 검증까지 한 번에 끝낸다. "featureN 만들어줘", "PR 브랜치 정리해줘", "히스토리 정리해줘", "PR용으로 커밋 다시 담아줘" 같은 요청에 적용.
---

# PR용 히스토리 구성

**기능 하나가 끝났을 때 한 번 하는 절차다.** 작업 도중의 커밋은 `git-conventions`를 따른다.

> **전제** — 무엇을 어느 브랜치에 담을지는 `git-conventions`의 «브랜치별 문서 소유» 표가 정한다.
> 커밋 메시지 형식도 그쪽을 따른다. 여기에 다시 적지 않는다.

**시행착오를 그대로 올리지 않는다.** 작업 브랜치에는 되돌린 커밋, 뒤늦게 메운 구멍,
문서와 코드가 섞인 커밋이 남기 마련이다.

**두 브랜치를 모두 담아야 끝난다.** `featureN`만 만들고 멈추지 않는다.
`_work`에 섞여 있던 `.claude/**` 는 소유 브랜치로 옮기기 전까지 **어디에도 반영되지 않은 상태**다.
1~3단계를 한 번에 진행하고, 중간에 끊어 "남은 일"로 넘기지 않는다.

### 1단계: `featureN` 에 구현을 담는다

`main`에서 새 브랜치를 만들고 **최종 결과물을 의존 순서로 다시 커밋한다.**

```bash
git checkout -b featureN main

# 단계별로 featureN_work의 최종 내용을 가져와 담는다.
# 구현 브랜치 소유 경로만 가져온다 — git-conventions 의 "브랜치별 문서 소유" 표가 정한다
git checkout featureN_work -- apps/*/src/ docs/도메인모델/
./gradlew :apps:commerce-api:test    # 커밋 전마다
git commit -m "..."
```

체리픽보다 이 방식이 낫다 — 중간 상태가 아니라 최종 내용이 단계별로 나뉘어 담기므로
각 커밋이 온전하고 테스트도 통과한다.

**커밋 순서는 개발 순서가 아니라 의존 순서다.** 밖에서 안으로(outside-in) 개발했더라도
의존 방향은 그대로 안쪽을 향하므로, 진입점부터 담으면 첫 커밋이 컴파일되지 않는다.

### 2단계: `claude_docs` 에 도구·문서 변경을 담는다

`_work`에 섞여 들어간 `.claude/**` 등은 `featureN`에 담지 않고 `claude_docs`로 옮긴다.

```bash
git checkout claude_docs
git checkout featureN_work -- .claude/ apps/commerce-api/CLAUDE.md docs/참고자료/
git commit -m "chore: [스킬] ..."
```

옮길 것이 없으면 **"없음"이라고 확인한 뒤** 넘어간다. 확인 없이 건너뛰는 것과 구분한다.

```bash
# 옮길 것이 있는지 먼저 본다 (비어 있으면 2단계는 "없음")
git diff --name-only claude_docs featureN_work -- .claude/ apps/commerce-api/CLAUDE.md docs/참고자료/
```

### 3단계: 검증하고 `featureN` 으로 돌아온다

```bash
# 코드가 작업 브랜치와 완전히 일치하는가 (비어 있어야 함)
git diff featureN featureN_work -- apps/*/src/

# 의도한 파일만 들어갔는가
git -c core.quotepath=false diff --name-only main featureN

# claude_docs 소유 파일이 featureN 에 섞이지 않았는가 (비어 있어야 함)
git diff --name-only main featureN -- .claude/ docs/참고자료/ apps/commerce-api/CLAUDE.md

# claude_docs 가 작업 브랜치와 일치하는가 (비어 있어야 함)
git diff --name-only claude_docs featureN_work -- .claude/

git checkout featureN    # 작업 브랜치로 돌아온다. claude_docs 에 남아 있지 않도록
```

`featureN_work`는 지우지 않고 남겨둔다.

### 완료 보고 (그대로 사용)

**양쪽을 나란히 적는다.** 한쪽이 비면 그 자리에서 드러난다.

```
✅ PR 히스토리 구성 완료

featureN:    {N}개 커밋 — {요약}
claude_docs: {N}개 커밋 — {요약}   (또는 "옮길 파일 없음")

검증: 코드 일치 ✓ / 의도한 파일만 ✓ / claude_docs 소유 파일 미혼입 ✓
현재 브랜치: featureN
```
