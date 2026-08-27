---
name: pr-history
description: 기능 작업을 마치고 PR용 브랜치를 구성할 때 사용. main에서 featureN을 만들어 개발 순서 그대로 다시 커밋하고, _work에 섞인 .claude/** 등은 claude_docs로 옮긴 뒤 검증까지 한 번에 끝낸다. "featureN 만들어줘", "PR 브랜치 정리해줘", "히스토리 정리해줘", "PR용으로 커밋 다시 담아줘" 같은 요청에 적용.
---

# PR용 히스토리 구성

**기능 하나가 끝났을 때 한 번 하는 절차다.** 작업 도중의 커밋은 `git-conventions`를 따른다.
PR 설명 문서는 `pr-description` 스킬을 따른다.

> **전제** — 무엇을 어느 브랜치에 담을지는 `git-conventions`의 «브랜치별 문서 소유» 표가 정한다.
> 커밋 메시지 형식도 그쪽을 따른다. 여기에 다시 적지 않는다.

**시행착오를 그대로 올리지 않는다.** 작업 브랜치에는 되돌린 커밋, 뒤늦게 메운 구멍,
문서와 코드가 섞인 커밋이 남기 마련이다.

**두 브랜치를 모두 담아야 끝난다.** `featureN`만 만들고 멈추지 않는다.
`_work`에 섞여 있던 `.claude/**` 는 소유 브랜치로 옮기기 전까지 **어디에도 반영되지 않은 상태**다.
1~3단계를 한 번에 진행하고, 중간에 끊어 "남은 일"로 넘기지 않는다.

### 1단계: `featureN` 에 구현을 담는다

`main`에서 새 브랜치를 만들고 **개발 순서 그대로 다시 커밋한다.**

```bash
git checkout -b featureN main

# 개발 단계마다 그 단계의 상태를 담는다.
# 구현 브랜치 소유 경로만 가져온다 — git-conventions 의 "브랜치별 문서 소유" 표가 정한다
git checkout featureN_work -- apps/*/src/ docs/도메인모델/
./gradlew :apps:commerce-api:test    # 커밋 전마다
git commit -m "..."
```

**커밋 순서는 개발 순서를 따른다.** 밖에서 안으로(outside-in) 개발했으면 PR도 밖에서
안으로 담는다. 리뷰어가 **API 계약을 먼저 보고 그 아래를 따라 내려가도록** 하기 위해서다.
`tfd-workflow-outside-in` 으로 만들었다면 그 스킬의 단계가 곧 커밋 단위다.

아래 레이어는 개발 때와 같이 **계약만 담고 본문은 스켈레톤으로 막는다.** 그래야 각 커밋이
컴파일되고 테스트도 통과한다. 아직 돌 수 없는 테스트는 `@Disabled` 로 두고, 마지막 봉합
커밋에서 스켈레톤을 걷고 `@Disabled` 를 켠다.

> **스켈레톤과 `@Disabled` 가 PR 중간에 보이는 것은 감수한다.** 그것이 outside-in 이 실제로
> 진행된 모습이고, 마지막 커밋에서 전부 걷힌다. 대신 **마지막 커밋에서 반드시 0건임을 확인한다** —
> 남으면 그 경로는 런타임에 500이 된다.

체리픽보다 이 방식이 낫다 — `_work` 의 시행착오(되돌린 커밋, 뒤늦게 메운 구멍, 문서와 코드가
섞인 커밋)를 그대로 옮기지 않고, **각 단계의 결론만** 담기 때문이다. 보정 커밋은 그것이
고치는 단계에 접어 넣는다.

> 안에서 밖으로(`tfd-workflow`) 개발했다면 그 순서가 곧 의존 순서라 스켈레톤이 필요 없다.

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

# 마지막 커밋에 스켈레톤과 @Disabled 가 남지 않았는가 (둘 다 0건이어야 함)
grep -rn "UnsupportedOperationException" apps/commerce-api/src/main
grep -rn "@Disabled" apps/commerce-api/src/test

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
