---
name: tfd-workflow-outside-in
description: 새 기능을 밖에서 안으로(outside-in) TFD로 추가할 때 사용. API 진입점부터 세우고 아래 레이어는 계약만 만들어 스켈레톤으로 막은 뒤, 단계마다 한 겹씩 채워 내려간다. "컨트롤러부터 만들자", "API 진입점부터", "밖에서 안으로", "outside-in으로 개발하자" 같은 요청에 적용. **이 프로젝트의 기본 워크플로다.** 반대 방향(도메인부터)은 사용자가 명시할 때만 tfd-workflow 를 쓴다.
---

# TFD Workflow (Outside-In) — 밖에서 안으로 기능 추가

## 핵심 원칙

1. **한 번에 한 레이어만 진행한다.** 절대 여러 레이어를 동시에 작성하지 않음.
2. **테스트가 먼저, 구현이 나중.** 컴파일 안 되어도 테스트부터.
3. **각 레이어 종료 시 반드시 멈추고 사용자 확인을 받는다.** 자동으로 다음 레이어 진행 금지.
4. **밖에서 안으로 진행한다.** interface → application → domain → infrastructure 순서.
5. **아래 레이어는 계약만 만든다.** 지금 레이어가 부르는 대상의 **시그니처와 DTO만** 만들고
   본문은 `UnsupportedOperationException` 스켈레톤으로 막는다. 동작은 다음 단계에서 채운다.
6. **테스트는 방향과 무관하게 관례대로 쓴다.** 밖에서 안으로 만든다고 테스트 작성 방식을 바꾸지 않는다.
   아직 돌 수 없는 테스트는 **`@Disabled`로 두고** 스택이 이어지는 단계에서 켠다.
   목으로 협력자를 다 막아 억지로 초록불을 만들지 않는다 — `tfd-workflow`가 경고하는
   *"내가 짠 호출 순서를 내가 검증하는"* 테스트가 된다.
7. **최소한만 구현한다.** 테스트가 통과하는 가장 작은 코드만. 추측성 기능 추가 금지.

`architecture-rules` skill의 모든 규칙은 이 워크플로우 전체에 적용됨.

> **방향만 다르고 나머지는 `tfd-workflow`와 같다.** 레이어 의존 방향(interfaces → application → domain
> ← infrastructure)은 **바뀌지 않는다.** 바뀌는 것은 *만드는 순서*뿐이다.

### 밖에서 안으로 할 때 감수하는 것

- **중간 단계에는 실행되는 검증이 거의 없다.** 아래가 스켈레톤이라 대부분의 테스트가 `@Disabled`다.
  이 구간을 목으로 메우려 하지 말 것 — 어차피 검증할 동작이 아직 없고, 남는 건 목 배선 확인뿐이다.
  안전망이 마지막 단계에 몰리는 것을 줄이려고 **3단계에서 도메인만 pitest 로 먼저 본다.**
- **스켈레톤이 남으면 조용히 500이 된다.** 마지막 단계에서 반드시 전수 확인한다.

---

## 0단계: 시작 전 합의 (필수)

새 기능 요청을 받으면 **즉시 코드를 작성하지 말고** 먼저 다음을 확인:

### 0.1 도메인 명세 읽기
- `docs/도메인모델/` 안의 해당 도메인 문서를 먼저 읽음
- 명세가 없거나 불명확하면 사용자에게 질문

### 0.2 작업 범위 합의
사용자에게 다음 항목을 정리해서 보여주고 확인받음:

```
## 작업 범위 합의

**기능명**: (예: 브랜드 등록)

**HTTP 엔드포인트**:
  - POST /api/v1/brands

**유스케이스**:
  - RegisterBrandUseCase

**도메인 객체**:
  - 새로 만들 것: Brand, BrandService

**infrastructure 구성요소**:
  - BrandRepository (JpaRepository + RepositoryImpl)

**참고할 기존 모듈**: Member 모듈 (가장 유사함)

이대로 진행할까요?
```

> 항목 순서를 엔드포인트부터 적는다. 만드는 순서와 같아야 합의 내용이 곧 작업 순서가 된다.

### 0.3 사용자 확인 받기
- 사용자가 "OK"라고 하기 전까지 1단계로 넘어가지 않음
- 범위가 너무 크면 더 작게 쪼개자고 제안

---

## 1단계: Interface Layer

→ `architecture-rules` 의 `references/controller.md` 를 따름. 여기서는 큰 흐름만.

**API 표면을 먼저 확정하고, UseCase는 계약만 만든다.**

### 진행 순서

1. **Controller 슬라이스 테스트 작성** (`@WebMvcTest` + `@MockitoBean`으로 UseCase 대체)
    - 정상 요청/응답
    - 필수 헤더 누락, 검증 실패 (400)
    - UseCase가 던진 `CoreException` → HTTP 상태 매핑 (`ErrorType`)
    - 응답에 내부 식별자를 노출하지 않는지
2. **`*V1Dto` 정의** (Request, Response record)
    - `Request.toInfo()`, `Response.from(Result)`
3. **`*V1ApiSpec` 인터페이스 작성** (Swagger 어노테이션)
4. **`*V1Controller` 작성** (`implements *V1ApiSpec`)
5. **아래 레이어의 계약만 작성**
    - `*UseCaseDto`의 `Info`/`Result` record는 **완성형으로** 만든다. Controller가 실제로 변환하기 때문
    - `*UseCase`는 **시그니처만**. 본문은 `throw new UnsupportedOperationException("2단계에서 구현")`
    - 응답 타입에 도메인 enum이 필요하면 그 enum만 앞당겨 만든다. 행위(상태 전이 규칙 등)는 붙이지 않는다
6. **E2E 테스트를 `@Disabled`로 작성**
    - 완성 목표를 코드에 먼저 남기는 용도. 마지막 단계에서 `@Disabled`를 뗀다
    - `@Disabled("N단계에서 활성화 — UseCase가 아직 스켈레톤이다")`처럼 사유를 남긴다
7. **슬라이스 테스트 통과 확인**

### 완료 조건
- [ ] Controller 슬라이스 테스트 통과
- [ ] DTO 변환 체인 준수 (Request → Info, Result → Response)
- [ ] Controller에 비즈니스 로직 없음, `@Transactional` 없음
- [ ] 예외 처리를 Controller에 넣지 않음 (`ApiControllerAdvice`가 이미 처리)
- [ ] Swagger 명세는 `*V1ApiSpec`에 분리
- [ ] E2E 테스트가 `@Disabled`로 존재

### 종료 멘트 (그대로 사용)
```
✅ Interface Layer 완료

작성된 것:
- V1Controller, V1ApiSpec, V1Dto: ...
- Controller 슬라이스 테스트: ...
- E2E 테스트(@Disabled): ...
- 계약만 만든 것: {UseCase 스켈레톤, UseCaseDto}

다음으로 Application Layer를 진행할까요?
API 표면이 잘못된 부분이 있으면 먼저 수정 요청해주세요.
```

여기서 **반드시 멈추고** 사용자 응답 대기.

---

## 2단계: Application Layer

→ `architecture-rules` 의 `references/usecase.md` 를 따름.

**UseCase 본문을 채우고, 도메인 서비스는 계약만 만든다.**

### 진행 순서

1. **UseCase 통합 테스트를 `@Disabled`로 작성** (`@SpringBootTest` + 실제 빈/DB)
    - 관례대로 쓴다. 도메인이 스켈레톤이라 지금은 돌지 않으므로 `@Disabled`로 두고 4단계에서 켠다
    - `@Disabled("N단계에서 활성화 — 도메인·인프라가 아직 스켈레톤이다")`처럼 사유를 남긴다
    - 정상 흐름, 비즈니스 예외 시나리오(`CoreException`), 멱등성, 동시성
    - **협력자를 목으로 막지 않는다.** 통제 불가능한 의존성만 `@MockitoSpyBean`으로 대체한다
2. **`*UseCase` 구현** (`@Component`, `@Transactional`)
    - 트랜잭션 경계가 UseCase와 갈라져야 하면 `*Processor`를 둔다
      (예: 커밋 시점에 터지는 DB 제약 위반을 트랜잭션 **밖**에서 변환해야 할 때)
3. **아래 레이어의 계약만 작성**
    - `*Service`, `*ServiceDto.*Command` 시그니처. 서비스 본문은 스켈레톤
4. **컴파일과 `@Disabled` 상태 확인** (이 단계에서 통과시킬 수 있는 테스트는 없다)

### 완료 조건
- [ ] UseCase 통합 테스트가 `@SpringBootTest` + `@Disabled`로 작성됨 (목으로 채우지 않았음)
- [ ] DTO 변환 체인 준수 (Info → Command, Query → Result)
- [ ] 트랜잭션 경계 명시 (`@Transactional`)
- [ ] 비즈니스 로직은 도메인에 위임 (UseCase는 흐름만)
- [ ] 1단계의 `UnsupportedOperationException`이 UseCase에서 사라졌음

### 종료 멘트
```
✅ Application Layer 완료

작성된 것:
- UseCase(+Processor): ...
- UseCase 통합 테스트(@Disabled): ...
- 계약만 만든 것: {Service 스켈레톤, ServiceDto}

다음으로 Domain Layer를 진행할까요?
```

**반드시 멈추고** 대기.

---

## 3단계: Domain Layer

→ `architecture-rules` 의 `references/domain-modeling.md` · `references/domain-service.md` 를 따름.

**엔티티/값객체를 먼저 끝내고, 그 다음에 도메인 서비스를 만든다. 둘을 한 번에 작성하지 않는다.**
한 번에 리뷰할 변경량을 줄이기 위해서다.

### 3-A. 엔티티 / 값객체

1. **도메인 객체 테스트 작성** (`*Test.java`)
    - 생성/팩토리 메서드의 불변식 검증
    - 비즈니스 규칙 위반 시나리오
    - 상태 전이 메서드
2. **테스트 컴파일되도록 도메인 객체 최소 구현**
    - 1단계에서 앞당겨 만든 enum이 있으면 여기서 행위를 채운다
    - 기존 도메인에 없어서 이 기능이 필요로 하는 메서드도 여기서 채운다
3. **테스트 통과 확인**

#### 3-A 종료 멘트 (그대로 사용)
```
✅ 엔티티 완료

작성된 것:
- {도메인 객체}: src/.../domain/{도메인}/{Class}.java
- {도메인 객체} 테스트: src/test/.../domain/{도메인}/{Class}Test.java

다음으로 도메인 서비스를 진행할까요?
엔티티가 잘못된 부분이 있으면 먼저 수정 요청해주세요.
```

여기서 **반드시 멈추고** 사용자 응답 대기.

### 3-B. 도메인 서비스 + Repository 인터페이스

> 도메인 서비스가 필요 없는 도메인이면 4~6을 건너뛰고 7만 수행한다.

4. **도메인 서비스 테스트 작성** (Repository는 mock)
5. **테스트 컴파일되도록 도메인 서비스 최소 구현** — 2단계 스켈레톤을 걷어낸다
6. **테스트 통과 확인**
7. **Repository 인터페이스 정의** (구현 X, 메서드 시그니처만)
8. **뮤테이션 검증** — `./gradlew :apps:commerce-api:pitest`

    밖에서 안으로 만들 때 **처음으로 진짜 초록불이 오는 구간이 여기다.** 아래는 아직 스켈레톤이고
    위는 `@Disabled` 라 실행되는 검증이 도메인밖에 없다. 그래서 이 단계에서 도메인 테스트가
    실제로 결함을 잡는지 확인하고 넘어간다 (20초). 판정 기준과 대응은 `test-effectiveness` 스킬에 있다.

### 완료 조건
- [ ] 도메인 객체 단위 테스트 모두 통과
- [ ] 엔티티 완료 시점(3-A)에 사용자 확인을 받았음
- [ ] 도메인 서비스가 있다면 단위 테스트 통과 (Repository는 mock)
- [ ] Repository 인터페이스 정의됨
- [ ] **`pitest` 의 테스트 강도 100%.** SURVIVED 가 있으면 프로덕션이 아니라 테스트를 고쳤음
- [ ] `architecture-rules`의 도메인 레이어 규칙 위반 없음

### 종료 멘트
```
✅ Domain Layer 완료

작성된 것:
- {도메인 객체}: ...
- {도메인 객체} 테스트: ...
- 도메인 서비스: ...
- Repository 인터페이스: ...

뮤테이션: 변이 {N}개 중 {M}개 제거, 살아남음 {K}개, 테스트 강도 {P}%

다음으로 Infrastructure Layer를 진행할까요?
```

**반드시 멈추고** 대기.

---

## 4단계: Infrastructure Layer

→ `architecture-rules` 의 "패키지 구조와 네이밍" 을 따름.

**여기서 처음으로 스프링 컨텍스트가 온전해진다.** Repository 구현 빈이 생겨야 관통 경로가 이어진다.

### Repository 테스트 작성 기준

→ `architecture-rules` 의 **"Repository 테스트 작성 기준"** 을 따른다. 여기에 옮겨 적지 않는다.

### 진행 순서
1. **(필요 시) Repository 통합 테스트 작성**
    - 위 "Repository 테스트 작성 기준"을 먼저 검토
    - 메서드 쿼리만 쓰는 단순 CRUD라면 이 단계 생략
2. **JpaRepository 인터페이스 작성**
3. **RepositoryImpl 작성** (`domain.*Repository` 구현)
4. **테스트가 있다면 통과 확인**

### 완료 조건
- [ ] Repository 테스트 작성 기준에 부합하는 테스트가 있다면 통과
- [ ] `domain.*Repository` 인터페이스의 모든 메서드 구현됨
- [ ] 별도 Entity 클래스 만들지 않음 (도메인 엔티티 직접 사용)

### 종료 멘트
```
✅ Infrastructure Layer 완료

작성된 것:
- JpaRepository: ...
- RepositoryImpl: ...
- Repository 통합 테스트: ... (작성한 경우만)

다음으로 E2E 봉합을 진행할까요?
```

**반드시 멈추고** 대기.

---

## 5단계: E2E 봉합

**밖에서 안으로 만든 스택이 실제로 이어졌는지 확인하는 단계다.** 새 기능을 추가하지 않는다.

### 진행 순서
1. **앞 단계에서 `@Disabled`로 둔 테스트를 전부 켠다** (1단계 E2E, 2단계 UseCase 통합 테스트)
2. **E2E 통과 확인**
3. **스켈레톤 잔존 전수 확인**
   ```bash
   grep -rn "UnsupportedOperationException" apps/commerce-api/src/main
   ```
   결과가 **0건**이어야 한다. 남아 있으면 그 경로는 런타임에 500이 된다
4. **전체 테스트 통과 확인**
   ```bash
   ./gradlew :apps:commerce-api:test
   ```
5. **실효성 검증 — 커밋 직전 마지막 단계** → `test-effectiveness` 스킬을 따른다

    도메인은 3단계에서 pitest 가 이미 봤다. 여기서는 **pitest 가 닿지 않는 곳**만 손으로 심는다 —
    `application` · `interfaces` · 예외 변환 경로. 전체가 초록불이 된 지금이 처음이자 유일한 기회다.
    대상은 이번 기능이 건드린 코드로 좁힌다.

### 완료 조건
- [ ] `@Disabled`가 하나도 남아 있지 않고 전부 통과
- [ ] `UnsupportedOperationException` 검색 결과 0건
- [ ] 전체 스위트 초록불 (중간 단계에서 깨져 있던 것이 여기서 해소됨)
- [ ] **`test-effectiveness` 로 application · interfaces · 예외 변환 경로를 검증했음**
- [ ] **심은 결함이 원복됐음** — `git diff -- apps/commerce-api/src/main` 이 깨끗하다

### 종료 멘트
```
✅ E2E 봉합 완료
✅ 기능 "{기능명}" 전체 완료

작성된 것:
- E2E 테스트 활성화: ...

스켈레톤 잔존: 0건
전체 테스트: {N}개 통과
실효성 검증: {심은 자리 N곳}, 고친 테스트 {M}개
```

---

## 위반 감지 시 행동

작업 도중 다음 상황이 발견되면 **즉시 작업 중단하고 사용자에게 보고**:

- `architecture-rules`의 의존성 방향 위반
- DTO 변환 체인 우회
- `@Transactional` 위치 오류
- 도메인이 인프라/Spring에 잘못 의존
- 표준 예외 직접 throw
- **아래 레이어를 계약보다 많이 구현함** (이번 단계에서 필요 없는 동작까지 미리 작성)

보고 형식:
```
⚠️ 아키텍처 규칙 위반 감지

위반: (구체적으로)
파일: (경로)
참조 규칙: architecture-rules > (섹션명)

수정 방향: (제안)

계속 진행하기 전에 어떻게 처리할까요?
```

---

## 자주 하는 실수 (체크리스트)

매 레이어 종료 전 자체 검증:

- [ ] 테스트 먼저 썼는가? (테스트 없이 구현부터 작성 X)
- [ ] 한 레이어만 건드렸는가? (아래 레이어를 계약 이상으로 구현 X)
- [ ] 아래 레이어를 **스켈레톤/목으로 막았는가?** (동작까지 미리 채우면 밖→안이 아니다)
- [ ] 엔티티와 도메인 서비스를 한 번에 작성하지 않았는가?
- [ ] 최소한만 구현했는가? (요구되지 않은 메서드/필드 추가 X)
- [ ] 참고 모듈(Member/Point) 스타일과 일치하는가?
- [ ] DTO 변환 체인 준수했는가?
- [ ] `CoreException` + `ErrorType` 사용했는가?
- [ ] 이전 단계에서 남긴 스켈레톤을 이번 단계에서 걷어냈는가?
- [ ] 다음 레이어로 진행하기 전 사용자 확인 받았는가?
