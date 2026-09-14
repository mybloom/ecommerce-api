---
name: tfd-workflow
description: 새 기능을 **안에서 밖으로**(도메인부터) TFD로 추가할 때 사용. **이 프로젝트의 기본은 tfd-workflow-outside-in 이다** — 둘 중 무엇을 쓸지 고민되면 항상 outside-in 을 쓴다. 이 스킬은 사용자가 "도메인부터", "안에서 밖으로" 라고 명시적으로 요청할 때만 쓴다.
---

# TFD Workflow (Inside-Out) — 새 기능 추가 절차

> **이 프로젝트의 기본은 `tfd-workflow-outside-in` 이다.** 주문 기능부터 밖에서 안으로 개발해 왔고,
> 당분간 이 스킬을 쓸 일이 없다. 사용자가 "도메인부터" 라고 명시할 때만 쓴다.

## 핵심 원칙

1. **한 번에 한 레이어만 진행한다.** 절대 여러 레이어를 동시에 작성하지 않음.
2. **테스트가 먼저, 구현이 나중.** 컴파일 안 되어도 테스트부터.
3. **각 레이어 종료 시 반드시 멈추고 사용자 확인을 받는다.** 자동으로 다음 레이어 진행 금지.
4. **안에서 밖으로 진행한다.** domain → infrastructure → application → interface 순서.
5. **최소한만 구현한다.** 테스트가 통과하는 가장 작은 코드만. 추측성 기능 추가 금지.

`architecture-rules` skill의 모든 규칙은 이 워크플로우 전체에 적용됨.

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

**도메인 객체**: 
  - 새로 만들 것: Brand, BrandService
  
** infrastructure 구성요소**:
  - BrandRepository (JpaRepository + RepositoryImpl)

**유스케이스**:
  - RegisterBrandUseCase

**HTTP 엔드포인트**:
  - POST /api/v1/brands

**참고할 기존 모듈**: Member 모듈 (가장 유사함)

이대로 진행할까요?
```

### 0.3 사용자 확인 받기
- 사용자가 "OK"라고 하기 전까지 1단계로 넘어가지 않음
- 범위가 너무 크면 더 작게 쪼개자고 제안

---

## 1단계: Domain Layer

→ `architecture-rules` 의 `references/domain-modeling.md` · `references/domain-service.md` 를 따름. 여기서는 큰 흐름만.

**엔티티/값객체를 먼저 끝내고, 그 다음에 도메인 서비스를 만든다. 둘을 한 번에 작성하지 않는다.**
한 번에 리뷰할 변경량을 줄이기 위해서다.

### 1-A. 엔티티 / 값객체

1. **도메인 객체 테스트 작성** (`*Test.java`)
    - 생성/팩토리 메서드의 불변식 검증
    - 비즈니스 규칙 위반 시나리오
    - 상태 전이 메서드 (`cancel()`, `activate()` 등)
2. **테스트 컴파일되도록 도메인 객체 최소 구현**
3. **테스트 통과 확인** (`./gradlew :apps:commerce-api:test --tests *도메인명*`)

#### 1-A 종료 멘트 (그대로 사용)
```
✅ 엔티티 완료

작성된 것:
- {도메인 객체}: src/.../domain/{도메인}/{Class}.java
- {도메인 객체} 테스트: src/test/.../domain/{도메인}/{Class}Test.java

다음으로 도메인 서비스를 진행할까요?
엔티티가 잘못된 부분이 있으면 먼저 수정 요청해주세요.
```

여기서 **반드시 멈추고** 사용자 응답 대기. 도메인 서비스 코드를 미리 작성하지 않는다.

### 1-B. 도메인 서비스 + Repository 인터페이스

> 도메인 서비스가 필요 없는 도메인이면 4~6을 건너뛰고 7만 수행한다.

4. **도메인 서비스 테스트 작성** (Repository는 mock)
5. **테스트 컴파일되도록 도메인 서비스 최소 구현**
6. **테스트 통과 확인**
7. **Repository 인터페이스 정의** (구현 X, 메서드 시그니처만)

### 완료 조건
- [ ] 도메인 객체 단위 테스트 모두 통과
- [ ] 엔티티 완료 시점(1-A)에 사용자 확인을 받았음
- [ ] 도메인 서비스가 있다면 단위 테스트 통과 (Repository는 mock)
- [ ] Repository 인터페이스 정의됨
- [ ] `architecture-rules`의 도메인 레이어 규칙 위반 없음

### 종료 멘트 (그대로 사용)
```
✅ Domain Layer 완료

작성된 것:
- {도메인 객체}: src/.../domain/{도메인}/{Class}.java
- {도메인 객체} 테스트: src/test/.../domain/{도메인}/{Class}Test.java
- Repository 인터페이스: ...

다음으로 Infrastructure Layer를 진행할까요?
도메인이 잘못된 부분이 있으면 먼저 수정 요청해주세요.
```

여기서 **반드시 멈추고** 사용자 응답 대기.

---

## 2단계: Infrastructure Layer

→ `architecture-rules` 의 "패키지 구조와 네이밍" 을 따름.

### Repository 테스트 작성 기준

→ `architecture-rules` 의 **"Repository 테스트 작성 기준"** 을 따른다. 여기에 옮겨 적지 않는다.

### 진행 순서
1. **(필요 시) Repository 통합 테스트 작성**
    - 위 "Repository 테스트 작성 기준"을 먼저 검토
    - 메서드 쿼리만 사용하는 단순 CRUD라면 이 단계 생략
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

다음으로 Application Layer를 진행할까요?
```

**반드시 멈추고** 대기.

---

## 3단계: Application Layer

→ `architecture-rules` 의 `references/usecase.md` 를 따름.

### 진행 순서
1. **UseCase 통합 테스트 작성** (`@SpringBootTest` + 실제 빈/DB)
    - 정상 흐름 (happy path)
    - 비즈니스 예외 시나리오 (`CoreException`)
    - 트랜잭션 롤백 케이스 (필요 시)
    - 의존성을 전부 mock하면 "내가 짠 호출 순서를 내가 검증하는" 테스트가 된다.
      실제 빈으로 엮고, 통제 불가능한 외부 의존성만 `@MockitoSpyBean`으로 대체한다.
2. **`*UseCaseDto.Info` / `*UseCaseDto.Result` record 정의**
    - `Info.toCommand()` 메서드
    - `Result.from(Query)` 정적 팩토리
3. **`*UseCase` 클래스 최소 구현** (`@Component`, `@Transactional`)
4. **테스트 통과 확인**

### 완료 조건
- [ ] UseCase 통합 테스트 통과
- [ ] DTO 변환 체인 준수 (Info → Command, Query → Result)
- [ ] 트랜잭션 경계 명시 (`@Transactional`)
- [ ] 비즈니스 로직은 도메인에 위임 (UseCase는 흐름만)

### 종료 멘트
```
✅ Application Layer 완료

작성된 것:
- UseCase: ...
- UseCaseDto (Info, Result): ...
- UseCase 통합 테스트: ...

다음으로 Interface Layer를 진행할까요?
```

**반드시 멈추고** 대기.

---

## 4단계: Interface Layer

→ `architecture-rules` 의 `references/controller.md` 를 따름.

### 진행 순서
1. **Controller E2E 테스트 작성** (`@SpringBootTest` + `MockMvc` 또는 `RestAssured`)
    - 정상 요청/응답
    - 검증 실패 (400)
    - 비즈니스 예외 매핑 (`ErrorType` → HTTP 상태)
2. **`*V1Dto` 정의** (Request, Response record)
    - `Request.toInfo()`, `Response.from(Result)`
3. **`*V1ApiSpec` 인터페이스 작성** (Swagger 어노테이션)
4. **`*V1Controller` 작성** (`implements *V1ApiSpec`)
5. **테스트 통과 확인**

### 완료 조건
- [ ] E2E 테스트 통과
- [ ] DTO 변환 체인 준수 (Request → Info, Result → Response)
- [ ] Controller에 비즈니스 로직 없음
- [ ] Swagger 명세는 `*V1ApiSpec`에 분리

### 명세 대사 (기능 종료 전 필수)

0단계에서 읽은 **도메인 명세를 다시 열어 규칙 한 줄씩 코드와 맞춰본다.**

- 명세의 규칙·결정 번호마다 **그것을 지키는 코드가 어디 있는지** 짚는다
- 짚히지 않는 것은 구현되지 않은 것이다. 사용자에게 보고한다
- 구현하지 않기로 했다면 명세에 그렇게 적는다 — 말없이 넘어가지 않는다

> `01_member.md` 에 "loginId, email 은 중복될 수 없다" 가 있었는데 `existsByLoginId` 만
> 구현되어 있었다. 테스트도 전부 통과했다. **없는 기능은 테스트가 알려주지 않는다.**

### 종료 멘트
```
✅ Interface Layer 완료
✅ 기능 "{기능명}" 전체 완료

작성된 것:
- V1Controller, V1ApiSpec, V1Dto: ...
- E2E 테스트: ...

전체 테스트 실행: ./gradlew :apps:commerce-api:test
```

---

## 위반 감지 시 행동

작업 도중 다음 상황이 발견되면 **즉시 작업 중단하고 사용자에게 보고**:

- `architecture-rules`의 의존성 방향 위반
- DTO 변환 체인 우회
- `@Transactional` 위치 오류
- 도메인이 인프라/Spring에 잘못 의존
- 표준 예외 직접 throw

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
- [ ] 한 레이어만 건드렸는가? (다음 레이어 코드까지 미리 작성 X)
- [ ] 엔티티와 도메인 서비스를 한 번에 작성하지 않았는가?
- [ ] 최소한만 구현했는가? (요구되지 않은 메서드/필드 추가 X)
- [ ] 참고 모듈(Member/Point) 스타일과 일치하는가?
- [ ] DTO 변환 체인 준수했는가?
- [ ] `CoreException` + `ErrorType` 사용했는가?
- [ ] 다음 레이어로 진행하기 전 사용자 확인 받았는가?
