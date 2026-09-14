# UseCase 컨벤션

애플리케이션 레이어에 속하는 UseCase(=Facade) 클래스 작성 가이드.

여러 도메인 서비스를 조합해 하나의 비즈니스 유스케이스를 구현하고, 트랜잭션 경계를 책임진다. 도메인 객체와 도메인 서비스는 도메인 레이어에 있고([domain-service.md](./domain-service.md)), UseCase는 그것들을 호출자(컨트롤러 등) 입장의 흐름으로 엮는 곳이다.

```
com.loopers.application.point.PointUseCase    ← 이 문서가 다루는 영역
com.loopers.domain.point.PointService          ← 도메인 서비스
```

## 1. 책임

UseCase의 책임:
- 여러 도메인 서비스를 조합해 유스케이스를 완성
- 트랜잭션 경계 정의
- 입출력 DTO 변환 (외부 ↔ 도메인 사이의 변환 지점)
- UseCase 단위의 검증 (예: 다른 도메인 객체의 존재 여부)

UseCase가 하지 않는 것:
- 도메인 객체의 비즈니스 규칙 검증 — 도메인 객체 책임
- 단일 도메인 객체의 라이프사이클 관리 — 도메인 서비스 책임

```java
// ✅ 여러 도메인을 조합
@Transactional
public RegisterResult register(RegisterInfo info) {
    Member member = memberService.register(info.toCommand());
    pointService.createInitialPoint(new CreateInitialCommand(member.getId()));
    return RegisterResult.from(member);
}

// ✅ 다른 도메인의 존재를 검증한 뒤 도메인 서비스 호출
@Transactional
public ChargeResult charge(ChargeInfo info) {
    memberService.retrieveById(new RetrieveByIdCommand(info.memberId()))
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                    "사용자를 찾을 수 없습니다. memberId=" + info.memberId()));

    ChargeQuery query = pointService.charge(info.toCommand());
    return ChargeResult.from(query);
}
```

## 2. 어노테이션과 의존성 주입

```java
@RequiredArgsConstructor
@Component
public class PointUseCase {
    private final PointService pointService;
    private final MemberService memberService;
}
```

- `@Component`: Spring 빈 등록.  도메인 서비스와 구분하기 위해 `@Component` 사용 
- `@RequiredArgsConstructor`: `final` 필드를 받는 생성자 자동 생성
- 필드는 `final`로 선언

`@Autowired`나 필드 주입은 사용하지 않는다.

## 3. 트랜잭션 경계

UseCase 메서드가 트랜잭션 경계다. 도메인 서비스 여러 개를 호출할 때, 그중 하나가 실패하면 전체가 롤백되어야 한다.

```java
import org.springframework.transaction.annotation.Transactional;  // ← 반드시 Spring의 어노테이션

// 상태 변경 메서드: @Transactional
@Transactional
public RegisterResult register(RegisterInfo info) {
    Member member = memberService.register(info.toCommand());
    pointService.createInitialPoint(...);  // 실패 시 위 register도 롤백
    return RegisterResult.from(member);
}

// 조회 전용 메서드: @Transactional(readOnly = true)
@Transactional(readOnly = true)
public RetrieveResult retrieve(RetrieveInfo info) {
    RetrieveQuery query = pointService.retrieve(info.toCommand());
    return RetrieveResult.from(query);
}
```

### `org.springframework.transaction.annotation.Transactional`을 써라

import 경로 실수가 자주 발생하므로 IDE 자동 import 시 항상 `org.springframework.transaction.annotation.Transactional`인지 확인한다.

### `readOnly = true`의 의미

조회 전용 메서드에 붙이면:
- JPA dirty checking 스킵 (성능 향상)
- 의도가 명시됨 ("이 메서드는 데이터를 변경하지 않는다")

## 4. DTO 흐름: Info → Command, Query → Result

UseCase 레이어와 도메인 레이어는 **각자의 DTO**를 갖는다. UseCase 레이어가 도메인 DTO를 직접 노출하지 않고, 자신의 DTO로 한 번 더 감싼다.

```
[컨트롤러]
   ↓ Request → Info
[UseCase]
   ↓ Info.toCommand() → Command
[도메인 서비스]
   ↓ Query
[UseCase]
   ↓ Result.from(Query)
[컨트롤러]
   ↓ Response.from(Result)
```

### 입력: `Info` → `toCommand()`

UseCase로 들어오는 입력은 `Info` 타입으로 받는다. UseCase 안에서는 `info.toCommand()`로 도메인 레이어의 `Command`로 변환해 도메인 서비스에 넘긴다.

```java
public record ChargeInfo(Long memberId, Long amount) {
    public PointServiceDto.ChargeCommand toCommand() {
        return new PointServiceDto.ChargeCommand(memberId, amount);
    }
}
```

### 출력: `Query` → `Result.from()`

도메인 서비스에서 받은 `Query`(또는 도메인 객체)를 UseCase의 `Result`로 변환해 반환한다.

```java
public record ChargeResult(Long memberId, Long amount, Long balance) {
    public static ChargeResult from(PointServiceDto.ChargeQuery query) {
        return new ChargeResult(query.memberId(), query.amount(), query.balance());
    }
}
```

### `from()`이 동작하지 않는 경우 — 직접 조립

`from(input)`은 **하나의 입력에서 모든 필드를 채울 수 있을 때**만 사용한다. 여러 입력을 조합해야 한다면 `from`을 쓰지 않고 UseCase에서 직접 조립한다.

```java
// 입력이 두 개 — Info(요청)와 Query(응답)를 모두 알아야 Result를 만들 수 있는 경우
@Transactional
public ChargeResult charge(ChargeInfo info) {
    ChargeQuery query = pointService.charge(info.toCommand());

    // ChargeResult가 (memberId, amount, balance)를 갖는데
    // amount는 Info에 있고 balance는 Query에 있어 from(query) 하나로는 부족하다면
    return new ChargeResult(query.memberId(), info.amount(), query.balance());
}
```

도메인 서비스가 충분한 정보를 반환하도록 `Query`를 설계하면 `from(query)` 한 번으로 끝난다. 그게 어렵거나 부자연스러우면 직접 조립한다.

### 변환 메서드 위치

| 변환 | 위치 |
|---|---|
| `Info → Command` | `Info` record의 인스턴스 메서드 (`toCommand()`) |
| `Query → Result` | `Result` record의 정적 팩토리 (`from()`) |
| `Member → Result` (도메인 객체에서) | `Result` record의 정적 팩토리 (`from()`) |

`from`은 **정적 팩토리** 패턴([domain-modeling.md](./domain-modeling.md) 참고). 생성자가 아닌 정적 메서드로 변환을 표현해 의도를 명확히 한다.

## 5. DTO 컨테이너 클래스 패턴

한 UseCase의 모든 DTO를 하나의 클래스(`XxxUseCaseDto`)에 모아두고, 안에 record로 정의한다.

```java
public class PointUseCaseDto {

    public record ChargeInfo(Long memberId, Long amount) {
        public PointServiceDto.ChargeCommand toCommand() { ... }
    }

    public record ChargeResult(Long memberId, Long amount, Long balance) {
        public static ChargeResult from(PointServiceDto.ChargeQuery query) { ... }
    }

    public record RetrieveInfo(Long memberId) {
        public PointServiceDto.RetrieveCommand toCommand() { ... }
    }

    public record RetrieveResult(Long memberId, Long balance) {
        public static RetrieveResult from(PointServiceDto.RetrieveQuery query) { ... }
    }
}
```

장점:
- 한 UseCase 관련 DTO를 한 파일에서 모두 볼 수 있음
- 클래스 파일 폭증 방지
- 컨트롤러에서 import할 때 `PointUseCaseDto.ChargeInfo`처럼 컨텍스트가 드러남

## 6. UseCase 단위 검증

UseCase 메서드가 추가로 검증할 수 있는 것:
- **다른 도메인 객체의 존재 여부** (예: 충전 시 회원이 존재하는가)
- **여러 도메인에 걸친 정합성** (예: 회원 등급에 따른 충전 한도)

```java
// 예: 회원 존재 확인 후 포인트 충전
memberService.retrieveById(new RetrieveByIdCommand(info.memberId()))
        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "사용자를 찾을 수 없습니다. memberId=" + info.memberId()));
```

도메인 객체 자체의 규칙(충전 금액 ≥ 1 등)은 도메인이 검증한다. UseCase는 도메인을 신뢰한다.

### 예외 메시지

도메인 컨벤션과 동일하게 **변수값을 메시지에 포함**해 디버깅을 돕는다.

```java
// ✅ 변수값 포함
"사용자를 찾을 수 없습니다. memberId=" + info.memberId()

// ❌ 변수 정보 없음
"사용자를 찾을 수 없습니다."
```

## 7. ErrorType 선택

도메인 레이어와 동일한 원칙을 따른다 ([domain-modeling.md](./domain-modeling.md) "예외 타입 구분" 참고).

| 상황 | ErrorType |
|---|---|
| 다른 도메인 객체 조회 실패 (예: 충전 시 회원 없음) | `NOT_FOUND` |
| UseCase 단위에서 발견하는 비즈니스 규칙 위반 | 도메인과 같은 기준 (`BAD_REQUEST`, `CONFLICT`) |

도메인 서비스가 던진 예외는 그대로 전파한다. UseCase에서 try-catch로 가로채서 다른 ErrorType으로 변환하지 않는다.

## 8. 단위 vs 통합 테스트

UseCase는 **`@SpringBootTest` 통합 테스트**로 검증하는 것이 자연스럽다. 이유:
- 트랜잭션 동작 검증 가능 (Spring 컨텍스트 필요)
- 여러 도메인 서비스의 협력을 실제로 확인
- 응답 DTO 변환과 DB 상태 변경의 일관성을 한 번에 검증

순수 Mockito 단위 테스트는 UseCase의 핵심 가치(트랜잭션 + 도메인 조합)를 검증하기 어려우므로 권장하지 않는다.

### 기본 구조

```java
@SpringBootTest
class PointUseCaseTest {

    private final PointUseCase pointUseCase;
    private final MemberUseCase memberUseCase;
    private final PointRepository pointRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public PointUseCaseTest(
            PointUseCase pointUseCase,
            MemberUseCase memberUseCase,
            PointRepository pointRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        // ...
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }
}
```

- 생성자 주입 (`@Autowired` + 생성자) — 새 의존성 추가 시 컴파일로 인지
- `@AfterEach`에서 DB 정리

### 검증 포인트 4가지

UseCase 통합 테스트는 다음을 검증한다:

**1. 정상 흐름의 데이터 일관성**
- 응답 DTO에 올바른 값이 담겼는가
- DB에 실제로 상태가 반영됐는가

```java
@Test
void register_initializesPointToZero() {
    RegisterInfo info = MemberFixture.aRegisterInfo();

    RegisterResult result = memberUseCase.register(info);

    Point savedPoint = pointRepository.findByMemberId(result.memberId()).orElseThrow();

    assertAll(
            () -> assertThat(result.memberId()).isNotNull(),
            () -> assertThat(savedPoint.getBalance().isZero()).isTrue()
    );
}
```

**2. UseCase가 직접 던지는 예외**

도메인 서비스가 아닌 UseCase 자체가 던지는 예외 (예: 다른 도메인의 존재 검증 실패).

```java
@Test
void throwsNotFound_whenMemberDoesNotExist() {
    long nonExistentMemberId = Long.MAX_VALUE;
    ChargeInfo info = new ChargeInfo(nonExistentMemberId, 500L);

    assertThatThrownBy(() -> pointUseCase.charge(info))
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
}
```

**3. 트랜잭션 경계 — 부분 실패 시 전체 롤백**

UseCase가 여러 도메인 서비스를 호출할 때, 도중에 실패하면 앞 단계도 롤백되는지 검증.

`@MockitoSpyBean`으로 도메인 서비스 일부를 가로채 일부러 실패시킨다.

```java
@MockitoSpyBean
private PointService pointService;

@Test
@DisplayName("포인트 생성 실패 시, 회원도 생성되지 않는다.")
void doesNotCreateMember_whenCreatePointFails() {
    RegisterInfo info = MemberFixture.aRegisterInfo();

    doThrow(new CoreException(ErrorType.CONFLICT, "포인트 생성 실패"))
            .when(pointService).createInitialPoint(any());

    assertThatThrownBy(() -> memberUseCase.register(info))
            .isInstanceOf(CoreException.class);

    assertThat(memberRepository.existsByLoginId(info.loginId())).isFalse();
}
```

이 검증은 **`@Transactional`이 정말 동작하는지를 확인**하는 핵심 테스트다. 여러 도메인을 조합하는 UseCase에는 반드시 하나 이상 작성한다.

**4. 외부 의존성 협력**

이메일 발송, 결제 게이트웨이 같은 외부 시스템 호출이 있다면 `@MockitoBean`으로 막고 `verify`로 호출됐는지 확인. (해당 케이스 없으면 생략)

### 검증하지 않는 것

- 도메인 객체의 비즈니스 규칙 (예: 충전 금액이 1 이상인지) — 도메인 테스트의 책임
- 도메인 서비스의 동작 — 도메인 서비스 테스트의 책임
- 도메인 예외가 그대로 전파되는가 — 자바 언어가 보장 (try-catch 없으면 자동 전파)

## 9. 픽스처와 셋업 패턴

UseCase 테스트의 셋업은 **도메인 흐름을 따라간다**. Repository로 직접 데이터를 박아넣기보다, 회원가입 같은 정상 경로로 데이터를 만든다.

```java
// ✅ 도메인 흐름 (회원 가입 시 Point도 자동 생성됨)
RegisterInfo registerInfo = MemberFixture.aRegisterInfo();
RegisterResult result = memberUseCase.register(registerInfo);
Long memberId = result.memberId();

// ❌ 직접 박아넣기 — Member와 Point의 정합성을 테스트 코드가 떠안게 됨
Member member = memberRepository.save(MemberFixture.aMember());
pointRepository.save(PointFixture.anInitialPoint(member.getId()));
```

장점:
- 도메인 정책 변경(예: 가입 시 추가 객체 생성)이 테스트에 자연스럽게 반영됨
- 테스트가 UseCase의 실제 사용 시나리오에 가까움

## 10. 금지 사항

- 필드 주입 (`@Autowired private XxxService ...`)
- `jakarta.transaction.Transactional` 사용 (Spring의 것을 사용)
- UseCase에서 도메인 규칙 재검증
- UseCase에서 도메인 예외를 가로채 다른 ErrorType으로 변환 (의도가 있을 때만, 그것도 신중히)
- 도메인 엔티티(`Member`, `Point`)를 컨트롤러까지 그대로 전달 — Result DTO로 변환
- 도메인 DTO(`MemberServiceDto.RegisterCommand`)를 컨트롤러까지 노출 — UseCase의 Info/Result로 감쌀 것
