# 테스트 코드 FP / FN 분석 결과

분석 기준: 2026-05-03  
분석 범위: `apps/commerce-api` 전체 테스트 코드 (`src/test`, 관련 모듈 `testFixtures` 포함)

---

## 사전 확인: 배경 설명과 실제 코드의 차이

**차이점 없음.** 1.3에서 설명한 패키지 구조, 계층 흐름, DTO 흐름은 실제 코드와 완전히 일치합니다.  
단, 아래 두 가지는 분석 중에 발견했으니 미리 알립니다.

1. **`PointUseCase.retrieve()`에 `@Transactional` 어노테이션이 없음.** 컨벤션은 조회 전용 메서드에 `@Transactional(readOnly = true)`를 요구하지만, 현재 없음. 도메인 서비스 컨벤션 문서에 "트랜잭션 정책은 도메인이 더 쌓이면 정리한다"는 TODO가 있어 의도적인 미확정으로 보이므로 컨벤션 일탈 목록에만 기재.

2. **`MemberSignV1ApiE2ETest`와 `MemberMeV1ApiE2ETest` 두 클래스가 모두 `GET /api/v1/members/me`를 E2E 테스트함.** 중복이지만 기능에는 영향 없음.

---

## 발견 사항 (영향도 높은 순)

---

### 1. `/me` 응답 필드(email, birthDate, gender)가 어느 계층에서도 검증되지 않음

- **위치**:
  - `MemberUseCaseTest.java` — `GetMemeber.me_existMember()`
  - `MemberMeV1ApiE2ETest.java` — `Retrieve.returnUserInfo_whenRetrieveMyInfo()`
  - `MemberSignV1ApiE2ETest.java` — `GetMemeber.returnUserInfo_whenRetrieveMyInfo()`
- **테스트 계층**: UseCase / E2E
- **유형**: FN
- **원인**: 세 테스트 모두 `id`만 최종 검증한다. `MeResult.from(Member)` → `MeResponse.from(MeResult)` 의 필드 변환 체인 전체에 걸쳐 `email`, `birthDate`, `gender`, `registerDate`(loginDate로 노출되는 createdAt)는 한 번도 단언되지 않는다.

```java
// MemberUseCaseTest — 현재
assertAll(
    () -> assertThat(result.id()).isEqualTo(savedMember.getId()),
    () -> assertThat(result.loginId()).isEqualTo(savedMember.getLoginId())
);

// MemberMeV1ApiE2ETest — 현재
assertThat(response.getBody().data().id()).isEqualTo(member.getId())
// email / birthDate / gender 없음
```

- **놓칠 수 있는 버그 시나리오**:
  - `MeResult.from(Member)`에서 `member.getEmail().getAddress()` 대신 `member.getLoginId()`를 실수로 매핑해도 통과.
  - `MeResponse`에서 `gender`와 `birthDate` 필드 순서가 뒤바뀌어도 통과.
  - `registerDate` 필드가 null로 반환되어도 통과.
- **영향도**: **높음** — 개인정보 조회 API의 핵심 응답 필드 전체가 검증 사각지대.
- **개선 방향**:

```java
// UseCase 테스트에 추가
assertAll(
    () -> assertThat(result.id()).isEqualTo(savedMember.getId()),
    () -> assertThat(result.loginId()).isEqualTo(savedMember.getLoginId()),
    () -> assertThat(result.email()).isEqualTo(MemberFixture.DEFAULT_EMAIL),
    () -> assertThat(result.birthDate()).isEqualTo(MemberFixture.DEFAULT_BIRTH_DATE),
    () -> assertThat(result.gender().name()).isEqualTo(MemberFixture.DEFAULT_STRING_GENDER)
);

// E2E 테스트에 추가
assertThat(response.getBody().data().email()).isEqualTo(MemberFixture.DEFAULT_EMAIL)
```

---

### 2. `MemberTest.createMember` — passwordHash 인코딩 여부 미검증

- **위치**: `MemberTest.java:35–38`
- **테스트 계층**: 도메인 객체
- **유형**: FN
- **원인**: `assertThat(member.getPasswordHash()).isNotNull()`은 평문 비밀번호를 저장해도 통과한다. `MemberFixture`의 스텁 `PasswordEncoder`가 `toUpperCase()`로 인코딩하므로, 실제 저장값이 `"SECRET"`(인코딩)인지 `"secret"`(평문)인지를 단언해야 인코딩 로직 검증이 완성된다.

```java
// 현재
assertThat(member.getPasswordHash()).isNotNull();

// 개선
assertThat(member.getPasswordHash())
    .isEqualTo(MemberFixture.DEFAULT_PASSWORD.toUpperCase()) // "SECRET"
    .isNotEqualTo(MemberFixture.DEFAULT_PASSWORD);           // "secret"
```

- **놓칠 수 있는 버그 시나리오**: `Member.create()` 내부에서 `passwordEncoder.encode(password)` 대신 `password`를 그대로 저장하도록 코드가 변경되어도 통과. 패스워드 평문 저장은 보안 취약점.
- **영향도**: **높음** — 패스워드 인코딩은 보안 핵심 경로. 컨벤션도 "도메인 객체의 책임 영역은 도메인 테스트에서 검증"이라고 명시.

---

### 3. `PointV1ControllerTest` — `@Valid` 위반 및 UseCase 인자 매핑 검증 전무

- **위치**: `PointV1ControllerTest.java` 전체
- **테스트 계층**: Controller 슬라이스
- **유형**: FN
- **원인**: 두 엔드포인트 모두 헤더 누락(400) 케이스 하나씩만 존재하며, 컨벤션이 명시하는 두 가지 핵심 검증이 모두 없다.

  **① `@Valid` 위반 → 400 케이스 없음**

  `ChargeRequest`의 `@Min(1) Long amount`:
  - `amount = 0` → 400이 되어야 하는데 테스트 없음
  - `amount`가 `null`이면 `@Min(1)`은 null을 무시하므로 Bean Validation을 통과한 채로 도메인까지 도달 → `Money.of(null)`이 BAD_REQUEST 발생. `@Valid`가 잡는 것이 아닌 도메인이 잡는 구조적 문제도 검증 사각지대.

  **② UseCase 인자 매핑 검증(`argThat`) 없음**

  헤더의 `memberId`가 `ChargeInfo.memberId`로 정확히 전달되는지 검증하는 테스트가 없다. `toInfo(memberId)`가 실수로 다른 값을 넘겨도 통과.

- **놓칠 수 있는 버그 시나리오**:
  - `@Min(1)`이 `@Min(0)`으로 변경되어도 컨트롤러 테스트는 통과.
  - `charge()` 메서드에서 `request.toInfo(memberId)` 대신 `request.toInfo(null)`로 인자를 잘못 넘겨도 통과.
- **영향도**: **높음** — 컨벤션 문서가 `PointV1ControllerTest`를 예시로 직접 다루며 `argThat` 검증을 필수로 명시함에도 해당 검증이 전혀 없음.
- **개선 방향**:

```java
// ① @Valid 위반
@Test
void returns400_whenAmountIsZero() throws Exception {
    mockMvc.perform(post(ENDPOINT)
            .header("X-MEMBER-ID", 1L)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"amount": 0}"""))
        .andExpect(status().isBadRequest());
}

// ② UseCase 인자 매핑
@Test
void passesMemberIdAndAmountToUseCase() throws Exception {
    when(pointUseCase.charge(any())).thenReturn(new PointUseCaseDto.ChargeResult(1L, 500L, 500L));

    mockMvc.perform(post(ENDPOINT)
            .header("X-MEMBER-ID", 1L)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"amount": 500}"""))
        .andExpect(status().isOk());

    verify(pointUseCase).charge(argThat(info ->
            info.memberId().equals(1L) && info.amount().equals(500L)));
}
```

---

### 4. `MemberV1ControllerTest` — UseCase 인자 매핑 및 예외→HTTP 상태 검증 없음

- **위치**: `MemberV1ControllerTest.java`
- **테스트 계층**: Controller 슬라이스
- **유형**: FN
- **원인**: `register` 관련 `@Valid` 두 케이스는 있지만, 컨벤션이 요구하는 나머지 두 종류가 없다.

  **① UseCase 인자 매핑(`argThat`) 없음** — RegisterRequest의 필드들이 `RegisterInfo`에 올바르게 넘어가는지 미검증.

  **② UseCase 예외 → HTTP 상태 코드 매핑 테스트 없음** — 예: UseCase가 `CONFLICT`를 던지면 409를 반환해야 하는데 테스트 없음. `MemberMeV1ApiE2ETest`와 `MemberSignV1ApiE2ETest`에서도 CONFLICT 케이스(중복 loginId 회원가입)가 E2E 레벨에서 테스트되지 않음.

- **놓칠 수 있는 버그 시나리오**:
  - `register()` 내부에서 `request.toInfo()` 변환 시 gender 필드를 빠뜨려도 통과.
  - `ApiControllerAdvice`가 `CoreException(CONFLICT)`를 잡아 409를 반환하는 경로가 회원 도메인 관점에서 한 번도 검증되지 않음 (Point 도메인 E2E에도 CONFLICT 케이스 없음).
- **영향도**: **중간** — 인자 매핑 버그는 E2E에서 잡힐 가능성이 있지만, CONFLICT→409 매핑은 어느 계층에도 명시적 테스트 없음.
- **개선 방향**:

```java
// UseCase가 CONFLICT를 던지면 409
@Test
void returns409_whenLoginIdAlreadyExists() throws Exception {
    when(memberUseCase.register(any()))
        .thenThrow(new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자입니다."));

    mockMvc.perform(post("/api/v1/members")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(MemberFixture.aRegisterRequest())))
        .andExpect(status().isConflict());
}
```

---

### 5. `PointUseCaseTest.charge_success` — DB 상태 미검증

- **위치**: `PointUseCaseTest.java:54–70`
- **테스트 계층**: UseCase
- **유형**: FN
- **원인**: `PointService.charge()`는 `save()`를 명시적으로 호출하지 않고 JPA dirty-checking에 의존한다. UseCase 테스트에서 반환된 DTO만 검증하고 실제 DB 상태를 읽지 않으므로, `@Transactional`이 없거나 dirty-checking이 비활성화된 상황에서도 테스트가 통과할 수 있다.

```java
// 현재 — DTO만 검증
assertAll(
    () -> assertThat(result.balance()).isEqualTo(initBalance + chargeAmount),
    () -> assertThat(result.amount()).isEqualTo(chargeAmount),
    () -> assertThat(result.memberId()).isEqualTo(existedMemberId)
);

// 개선 — DB 상태도 검증 (MemberUseCaseTest.register_initializesPointToZero 패턴 참고)
Point updatedPoint = pointRepository.findByMemberId(existedMemberId).orElseThrow();
assertThat(updatedPoint.getBalance().getAmount()).isEqualTo(initBalance + chargeAmount);
```

- **놓칠 수 있는 버그 시나리오**: `PointUseCase.charge()`에서 `@Transactional`이 제거되어 dirty-checking이 commit되지 않아도 반환 DTO(메모리 기준)가 올바르면 통과.
- **영향도**: **중간** — `MemberUseCaseTest`는 DB 상태를 검증하는 패턴을 잘 지키고 있어 일관성 문제이기도 함.

---

### 6. `null` 입력이 `@Valid`를 우회해 도메인까지 도달하는 경로 미커버

- **위치**:
  - `MemberV1Dto.registerRequest` — `@Size(min=3, max=15) String loginId` (NotNull 없음)
  - `PointV1Dto.ChargeRequest` — `@Min(1) Long amount` (NotNull 없음)
- **테스트 계층**: Controller 슬라이스 / E2E
- **유형**: FN
- **원인**: Jakarta Validation에서 `@Size`와 `@Min`은 null에 적용되지 않는다. null loginId는 Bean Validation을 통과한 뒤 `Member.create()`의 `requireNonNull()`에서 NPE가 발생하고, `ApiControllerAdvice`의 catch-all `Throwable` 핸들러가 500을 반환한다. null amount는 `PointServiceDto.ChargeCommand`의 compact constructor에서 `Money.of(null)` → BAD_REQUEST가 발생하지만, 컨트롤러 `@Valid`가 아닌 도메인에서 잡힌다.

- **놓칠 수 있는 버그 시나리오**: null loginId 요청 시 400(입력 검증 실패) 대신 500(내부 오류)이 반환될 수 있음. 클라이언트는 잘못된 입력을 보냈는데 서버 오류로 오해하게 됨.
- **영향도**: **중간** — 실제 API 계약 위반(400이 와야 할 자리에 500).
- **개선 방향**: `@NotBlank` (`loginId`), `@NotNull` (`amount`) 어노테이션 추가 후 컨트롤러 슬라이스 테스트에서 검증.

---

### 7. `PointUseCaseTest.retrieve` — `memberId` 미검증, 불필요한 isNotNull 단언

- **위치**: `PointUseCaseTest.java:98–101`
- **테스트 계층**: UseCase
- **유형**: FN
- **원인**: `assertThat(result).isNotNull()`은 다음 줄에서 `result.balance()`를 호출하는 순간 NPE로 대체되므로 정보량이 없다. 더 중요하게는 `result.memberId()`가 검증되지 않는다.

```java
// 현재
assertAll(
    () -> assertThat(result).isNotNull(),          // 의미 없음
    () -> assertThat(result.balance()).isEqualTo(existedPoint.getBalance().getAmount())
);

// 개선
assertAll(
    () -> assertThat(result.memberId()).isEqualTo(existedMemberId),
    () -> assertThat(result.balance()).isEqualTo(existedPoint.getBalance().getAmount())
);
```

- **놓칠 수 있는 버그 시나리오**: `RetrieveResult.from(query)` 에서 memberId를 잘못 매핑해도 통과.
- **영향도**: **낮음** — E2E에서 memberId를 검증하고 있어 완전한 사각지대는 아님.

---

### 8. `PointTest.Use` — `use(0)` 허용 vs `charge(0)` 거부의 정책 비대칭

- **위치**: `PointTest.java:110–118` (`keepsBalance_whenAmountIsZero`)
- **테스트 계층**: 도메인 객체
- **유형**: FN (정책이 의도적이라면 TN이지만, 현재 그 의도가 명시되지 않음)
- **원인**: `charge(0)`은 `BAD_REQUEST`를 발생시키지만, `use(0)`는 잔액 변화 없이 조용히 통과한다. 프로덕션 코드 `Point.use()`에는 0 입력에 대한 별도 검증이 없다. "0원 사용"이 비즈니스 상 의미 있는 케이스인지 정책이 명시되어 있지 않다.

- **놓칠 수 있는 버그 시나리오**: "포인트 사용은 1원 이상"이 실제 정책인 경우, `use(0)` 호출 시 예외가 발생해야 하는데 발생하지 않음. 현재 테스트는 이 동작을 "정상"으로 고정시켜버림.
- **영향도**: **낮음** — 정책이 명확히 정의되면 테스트를 수정하거나 프로덕션 코드에 검증을 추가해야 함. 현재는 테스트가 잘못된 동작을 정상으로 잠그는 FN의 가능성.

---

### 9. `EmailTest` — null 입력 미커버

- **위치**: `EmailTest.java`
- **테스트 계층**: 도메인 객체
- **유형**: FN
- **원인**: `new Email(null)` 시 `EMAIL_PATTERN.matcher(null)`에서 `NullPointerException`이 발생한다. `Money.of(null)`은 `CoreException(BAD_REQUEST)`를 던지는 반면 `Email(null)`은 NPE를 던지는 불일치가 있다. 이 케이스에 대한 테스트가 없어 동작이 명시되어 있지 않다.

- **놓칠 수 있는 버그 시나리오**: null 이메일로 회원 가입 시 BAD_REQUEST 대신 500이 반환될 수 있음. `MemberV1Dto.RegisterRequest`에 `@Email`만 있고 
  `@NotBlank`가 없으므로 null email이 컨트롤러를 통과할 수 있음(위 항목 6과 연결).
- **영향도**: **낮음** — 6번 항목과 연동하면 실제 런타임 경로.

---

## 추가 검토 필요

### A. `PointServiceTest.charge` — `save()` 호출 미검증이 의도적인가?

- **위치**: `PointServiceTest.java:80–93`
- **의문**: `PointService.charge()`는 dirty-checking에 의존하고 명시적 `save()`를 호출하지 않는다. `PointServiceTest`에는 `verify(pointRepository, never()).save(...)` 같은 단언도 없다. `MemberService.register()`는 `save()`를 명시적으로 호출하기 때문에 `verify(memberRepository).save(...)`가 자연스럽다.
- **확인 필요**: dirty-checking 의존 방식이 의도적 설계인지, 아니면 명시적 `save()`를 호출하도록 변경할 계획인지 팀 내 합의가 필요. 현재 서비스 테스트에서는 검증할 수 없는 부분이라 E2E로 커버되는 구조임.

### B. `PointUseCaseTest` — 트랜잭션 롤백 테스트 부재

- **위치**: `PointUseCaseTest.java`
- **의문**: `MemberUseCaseTest.doesNotCreatedMember_whenCreatePointFail()`처럼 `PointUseCase.charge()`에서 도중 실패 시 전체 롤백 여부를 검증하는 테스트가 없다. `PointUseCase.charge()`는 두 도메인 서비스를 호출하지만 첫 번째 호출(memberService.retrieveById)이 read-only라 rollback 검증의 필요성이 낮다. 그러나 향후 PointUseCase가 여러 상태 변경을 포함하게 되면 이 테스트가 필요해짐.
- **확인 필요**: 현재 설계에서 rollback 테스트가 의도적으로 생략된 것인지.

---

## 테스트가 아예 없는 영역 (구조적 FN)

아래 영역은 테스트 코드 자체가 없어 어떤 버그도 잡히지 않는다.

| 영역 | 설명 | 우선순위 |
|---|---|---|
| **중복 loginId 회원가입 (CONFLICT → 409)** | E2E/UseCase/Controller 어느 계층에도 없음 | 높음 |
| **중복 email 회원가입** | DB unique 제약이 있지만 서비스/UseCase 레벨에서 미검증 | 중간 |
| **포인트 잔액 부족 시 사용 (point.use 전체 흐름)** | `Point.use()`의 단위 테스트는 있으나 UseCase/E2E에서 사용 API가 없어 현재 노출되지 않음 (주문 흐름 내 차감이므로) | 도메인 구현 시 필요 |
| **`@Email` 형식 위반 → 400** | Controller 슬라이스에서 invalid email format 요청 케이스 없음 | 낮음 |
| **loginId 최대 길이(16자) 위반 → 400** | `@Size(max=15)` 위반 케이스 테스트 없음 | 낮음 |

---

## 컨벤션 일탈 목록

| 위치 | 위반 내용 |
|---|---|
| `PointUseCaseTest.java:75` | `nonExistentMemberId = 9999L` — 최근 커밋(5fb68b2)에서 MemberUseCaseTest는 `Long.MAX_VALUE`로 수정했으나 PointUseCaseTest는 미수정. 동일한 이유(도달 불가능한 값)로 통일 필요 |
| `PointUseCase.java:30` | `retrieve()` 메서드에 `@Transactional(readOnly = true)` 없음 — 컨벤션 위반 |
| `PointFixture.java:12–75` | 메서드마다 Javadoc(`/** */`) 주석 — 컨벤션("Default to writing no comments") 위반 |
| `MemberMeV1ApiE2ETest.java` | `MemberSignV1ApiE2ETest`와 `GET /api/v1/members/me` happy path 중복 커버 |

---

## 전체 요약

### 패턴 공통점

**FN 쪽에서 반복되는 패턴은 두 가지다.**

1. **응답 DTO 부분 검증** — "id만 맞으면 됐다"는 식의 검증이 UseCase, Controller, E2E 전 계층에 걸쳐 반복된다. 특히 Member의 `/me` 응답에서 email·birthDate·gender·registerDate가 전 계층에서 검증되지 않는 것이 가장 큰 사각지대다.

2. **Controller 슬라이스 테스트 미완성** — `PointV1ControllerTest`는 헤더 누락 케이스 두 개만 있고, 컨벤션이 요구하는 `@Valid` 위반 케이스와 `argThat` 인자 매핑 검증이 양쪽 컨트롤러 모두에서 부족하다. 컨벤션 문서가 `argThat` 검증을 "컨트롤러의 핵심 책임"으로 명시하고 있기 때문에 이 공백이 가장 눈에 띈다.

**FP는 구조적으로 크게 우려할 부분이 없다.** DB 격리(`truncateAllTables`)가 일관되게 적용되어 있고, 시간 의존성이나 정적 상태 공유 이슈도 발견되지 않았다. `ErrorType` 기반 예외 검증도 전반적으로 잘 지켜지고 있다.

### 우선 개선 순서

| 순위 | 항목 |
|---|---|
| 1 | `/me` 응답 필드(email, birthDate, gender) 검증 추가 (항목 1) |
| 2 | `MemberTest.createMember` — passwordHash 인코딩 검증 강화 (항목 2) |
| 3 | `PointV1ControllerTest` — `@Valid` 위반 및 `argThat` 추가 (항목 3) |
| 4 | `MemberV1ControllerTest` — CONFLICT→409 및 `argThat` 추가 (항목 4) |
| 5 | `PointUseCaseTest.charge_success` — DB 상태 검증 추가 (항목 5) |
| 6 | `@NotNull`/`@NotBlank` 누락으로 인한 null 우회 경로 보강 (항목 6) |
