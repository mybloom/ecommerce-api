# 테스트 작성 컨벤션

JUnit 5 + AssertJ + Mockito 기반 단위 테스트 작성 가이드.

## 1. 구조: `@Nested`로 메서드별 그룹화

테스트 대상 클래스의 각 행위(메서드)를 `@Nested` 클래스로 그룹화한다. 출력이 트리로 나와 어떤 메서드의 어떤 케이스가 깨졌는지 한눈에 보인다.

```java
class PointTest {

    @Nested
    @DisplayName("charge")
    class Charge {
        @Test
        @DisplayName("양수 금액을 충전하면 잔액이 증가한다")
        void increasesBalance_whenAmountIsPositive() { ... }
    }

    @Nested
    @DisplayName("use")
    class Use {
        @Test
        @DisplayName("보유 잔액보다 적은 금액을 차감하면 잔액이 감소한다")
        void decreasesBalance_whenAmountIsLessThanBalance() { ... }
    }
}
```

## 2. Given-When-Then을 주석으로 구분

`// given`, `// when`, `// then` 주석으로 나눈다. 빈 줄만으로 나누면 given이 커질 때 경계가 보이지 않는다.

```java
@Test
@DisplayName("두 금액을 더한 결과를 반환한다")
void returnsSum() {
    // given
    Money money1 = Money.of(1_000L);
    Money money2 = Money.of(500L);

    // when
    Money result = money1.add(money2);

    // then
    assertThat(result).isEqualTo(Money.of(1_500L));
}
```

실행과 검증이 물리적으로 붙어 있으면(`mockMvc.perform().andExpect()` 같은 체인)
`// when & then`으로 묶는다. 억지로 나누지 않는다.

한 블록 안에서 목적이 갈리면 한 줄 띄운다. 특히 given이 길어질 때 효과가 크다.

## 3. 한 테스트는 하나의 행위만 검증

여러 입력값을 한 테스트에 묶지 않는다. 첫 번째 단언이 실패하면 나머지는 실행되지 않아 문제 파악이 어렵다.

```java
// ❌ 여러 입력을 한 테스트에 묶음
@Test
void create_normal() {
    assertThat(Money.of(0L).getAmount()).isEqualTo(0L);
    assertThat(Money.of(1_000L).getAmount()).isEqualTo(1_000L);  // 위가 깨지면 실행 안 됨
}

// ✅ 단일 케이스
@Test
void create_normal() {
    // given
    long amount = 1_000L;

    // when
    Money money = Money.of(amount);

    // then
    assertThat(money.getAmount()).isEqualTo(1_000L);
}
```

여러 입력을 검증해야 한다면 `@ParameterizedTest`를 사용한다.

```java
@ParameterizedTest
@DisplayName("0 이상의 값으로 생성할 수 있다")
@ValueSource(longs = {0L, 1L, Long.MAX_VALUE})
void create_normal(long amount) {
    // when
    Money money = Money.of(amount);

    // then
    assertThat(money.getAmount()).isEqualTo(amount);
}
```

## 4. 단언이 2개 이상이면 반드시 `assertAll`

단언이 둘 이상이면 예외 없이 `assertAll`로 묶는다. 단독 `assertThat`은 단언이 하나일 때만 쓴다.
묶지 않으면 첫 단언이 깨지는 순간 나머지가 실행되지 않아, 무엇이 더 잘못됐는지 한 번에 알 수 없다.

단, **여러 입력 케이스를 묶는 용도로는 쓰지 않는다.** 그건 3번의 `@ParameterizedTest` 자리다.
`assertAll`이 묶는 것은 **한 실행 결과의 여러 측면**이다.

```java
// ✅ 같은 객체의 여러 속성
@Test
@DisplayName("Point 생성 시, 초기값이 0으로 생성된다.")
void createInitialPoint() {
    // given
    PointServiceDto.CreateInitialCommand command = anInitialCommand();

    // when
    Point point = Point.createInitial(command);

    // then
    assertAll(
            () -> assertThat(point.getMemberId()).isEqualTo(DEFAULT_MEMBER_ID),
            () -> assertThat(point.getBalance().isZero()).isTrue()
    );
}

// ✅ 응답 DTO의 여러 필드
@Test
void returnsIncreasedBalance_whenChargeSucceeds() {
    // given
    long chargeAmount = 500L;
    Long expectedBalance = 1_500L;   // 리터럴로 고정. 프로덕션의 덧셈을 따라 하지 않는다
    // ...

    // then
    assertAll(
            () -> assertThat(result.memberId()).isEqualTo(DEFAULT_MEMBER_ID),
            () -> assertThat(result.balance()).isEqualTo(expectedBalance)
    );
}
```

### 기대값은 리터럴 또는 픽스처 상수로 둔다

프로덕션의 계산을 테스트가 따라 하면 같은 실수를 양쪽이 함께 하고 통과한다.

```java
// ❌ 프로덕션의 덧셈을 테스트가 따라 한다
assertThat(result.balance()).isEqualTo(initialAmount + chargeAmount);

// ✅ 테스트가 통제하는 값
Long expectedBalance = 1_500L;
assertThat(result.balance()).isEqualTo(expectedBalance);
```

같은 이유로 기대값을 `member.toProfile()` 이나 `member.getLoginId()` 처럼 **입력에서
파생시키지 않는다.** 실제값과 출처가 같아져 동어반복이 된다.

## 5. 예외 검증은 `ErrorType`을 확인

메시지 부분 일치가 아니라 `ErrorType`을 검증한다. 메시지는 자연어라 자주 바뀌지만 `ErrorType`은 enum이라 변경 시 컴파일 에러로 잡힌다.

```java
// ✅ 권장
assertThatThrownBy(() -> point.charge(Money.of(0L)))
        .isInstanceOfSatisfying(CoreException.class, e ->
                assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));

// ❌ 메시지 부분 일치는 깨지기 쉬움
assertThatThrownBy(() -> point.charge(Money.of(0L)))
        .hasMessageContaining("충전 금액은 1 이상");
```

## 6. 픽스처는 별도 클래스로

같은 픽스처가 여러 테스트 파일에서 쓰이면 `XxxFixture` 클래스로 추출한다. 도메인의 정상 생성 경로(`createInitial → charge`)를 통해 객체를 만든다.

```java
public final class PointFixture {

    public static final long DEFAULT_MEMBER_ID = 1L;

    private PointFixture() {}

    public static Point anInitialPoint() {
        return Point.createInitial(anInitialCommand());
    }

    public static Point aPointWithBalance(long balance) {
        Point point = anInitialPoint();
        point.charge(Money.of(balance));
        return point;
    }

    public static PointServiceDto.CreateInitialCommand anInitialCommand() {
        return new PointServiceDto.CreateInitialCommand(DEFAULT_MEMBER_ID);
    }
}
```

사용 측은 `static import`로 호출한다:

```java
import static com.loopers.domain.point.PointFixture.*;

class PointTest {
    @Test
    void decreasesBalance_whenAmountIsLessThanBalance() {
        Point point = aPointWithBalance(1_000L);
        // ...
    }
}
```

### 픽스처 명명 규칙

- `a-` / `an-` 접두사로 "어떤 상태의 객체"라는 명사구 의미를 표현
    - `aPointWithBalance(1000L)` — "잔액 1000인 Point 하나"
    - `anInitialPoint()` — "초기 상태 Point 하나"
    - `anInitialCommand()` — "초기 생성용 Command 하나"
- 한 픽스처 클래스 내에서는 일관되게 적용

### 헬퍼는 의도가 있을 때만

헬퍼는 이름이 의도를 설명할 때만 가치 있다. 단순히 타이핑을 줄이는 별칭은 가독성을 떨어뜨린다.

```java
// ❌ 단순 별칭
private Point newPoint() {
    return Point.createInitial(new PointServiceDto.CreateInitialCommand(MEMBER_ID));
}

// ✅ 의도가 이름에 있음
public static Point aPointWithBalance(long balance) { ... }
```

### 테스트 상수의 위치

`MEMBER_ID` 같은 테스트 상수는 픽스처 클래스에 두고 import해서 쓴다. 한 테스트 클래스의 상수를 다른 테스트 클래스가 import하는 것은 테스트 간 결합이 되므로 피한다.

```java
// ❌ 테스트 클래스 간 결합
import static com.loopers.domain.point.PointTest.MEMBER_ID;

// ✅ 픽스처를 통해 공유
import static com.loopers.domain.point.PointFixture.DEFAULT_MEMBER_ID;
```

## 7. 도메인 서비스 테스트 (Mock 기반)

도메인 서비스(`PointService` 등)는 리포지토리 등 외부 의존을 갖는다. Mockito로 의존을 격리한 단위 테스트를 작성한다.

### 기본 구조

```java
@ExtendWith(MockitoExtension.class)
class PointServiceTest {
    private PointService pointService;

    @Mock
    private PointRepository pointRepository;

    @BeforeEach
    void setUp() {
        pointService = new PointService(pointRepository);
    }

    // ...
}
```

### 수동 생성자 주입을 원칙으로 (`@InjectMocks` 사용 금지)

`@BeforeEach`에서 직접 `new`로 인스턴스를 생성한다. 이유는 **새 의존성이 추가되었을 때 컴파일 단계에서 알아야 하기 때문**이다.

```java
// 서비스에 새 의존성 추가됨
public class PointService {
    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;  // ← 추가
}

// 수동 주입 → 모든 테스트에서 즉시 컴파일 에러 ✅
pointService = new PointService(pointRepository);
//                                              ↑ 컴파일 에러: 인자 부족

// @InjectMocks → 컴파일 통과, 실행 시점 NPE ❌
@InjectMocks
private PointService pointService;
```

리플렉션 주입은 정합성 보장이 약하다. 수동 주입을 원칙으로 한다.

### 클래식 Mockito 스타일로 통일

`when().thenReturn()` 형태를 사용한다. BDD 스타일(`given().willReturn()`)과 섞어 쓰지 않는다.

```java
// ✅ 클래식 스타일
when(pointRepository.findByMemberId(DEFAULT_MEMBER_ID))
        .thenReturn(Optional.empty());

when(pointRepository.save(any(Point.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

// verify는 두 스타일 공통
verify(pointRepository).save(any(Point.class));
verify(pointRepository, never()).save(any(Point.class));
```

`save`처럼 입력을 그대로 반환하는 동작은 `thenAnswer`로 모방한다 (실제 JPA 동작에 가까움).

### `verify`로 호출 여부 검증

상태만으로는 검증할 수 없는 동작(예: "이미 존재하면 save가 호출되지 않는다")은 `verify`로 직접 검증한다.

```java
@Test
void returnsExistingPoint_whenAlreadyExists() {
    // ...
    verify(pointRepository, never()).save(any(Point.class));
}
```

### 작성하지 않을 테스트

도메인 서비스에서 검증할 가치가 없는 테스트는 작성하지 않는다.

- **도메인 예외 전파 테스트** ❌ — `point.charge(...)`가 던진 예외가 그대로 전파되는 건 자바 언어가 보장. 서비스에 try-catch가 없는 한 검증할 가치 없음
- **도메인 동작의 재검증** ❌ — `Point.charge`의 동작은 `PointTest`에서 이미 검증

도메인 서비스 테스트는 다음을 검증한다:
- 서비스가 **하는 일** (리포지토리 조회, 도메인 호출, 응답 변환)
- 서비스가 **추가하는 검증** (NOT_FOUND, CONFLICT 등)
- 서비스가 **결합하는 로직** (도메인 객체 + 리포지토리)

### Stubbing은 "테스트가 실제로 필요로 하는 것"만

서비스 메서드를 한 줄씩 따라가며 "이 호출의 반환값이 다음 줄에서 사용되는가"를 묻는다.
사용되지 않으면 stub하지 않는다. Mockito는 stub되지 않은 메서드에 기본값(null/0/false)을
반환하므로, 그게 후속 로직에 영향을 주지 않으면 그대로 둔다.

```java
public Member register(...) {
    if (memberRepository.existsByLoginId(...)) {  // ← 분기에 사용 → stub 필요
        throw ...;
    }
    Member member = memberRepository.save(...);   // ← member를 이후에 안 씀 → stub 불필요
    return member;
}
```

```java
// ✅ 필요한 것만
when(memberRepository.existsByLoginId("testId")).thenReturn(false);
when(passwordEncoder.encode(anyString())).thenReturn("encoded");
// memberRepository.save() stubbing은 생략 — null이 반환되어도 흐름에 영향 없음

memberService.register(command);

verify(memberRepository).save(any(Member.class));
```

"미래 변경에 대비해 미리 깔아둔다"는 YAGNI에 어긋난다. stubbing이 늘어날수록 테스트가 검증하려는
것이 흐려진다.

### `thenAnswer + getArgument(0)`은 echo 용도면 의미가 약하다

```java
when(repo.save(any(Member.class)))
    .thenAnswer(inv -> inv.getArgument(0));
```

이 패턴은 두 가지 용도가 있다.

1. **NPE 회피용 setup** — 후속 로직이 `member.getId()` 같은 호출을 하면 null이면 NPE.
   이때는 검증 도구가 아니라 **환경 조성**이다.
2. **JPA 동작 모방** — save가 입력을 그대로 반환한다는 사실을 테스트에서 흉내.

받은 객체를 그대로 돌려준다는 사실 자체를 `assertThat(result).isSameAs(input)`으로 확인하는
것은 Mockito의 동작을 검증하는 셈이다. 의미 있는 검증이 아니다.

진짜 `thenAnswer`가 필요한 경우는 **인자에 따라 분기**하거나 **인자를 가공**해야 할 때다.

### 검증의 책임은 테스트 대상의 책임과 일치해야 한다

도메인 서비스 테스트에서 도메인 객체의 책임을 다시 검증하지 않는다.

```java
// ❌ MemberServiceTest에서 비밀번호 해싱을 검증
ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
verify(memberRepository).save(captor.capture());
assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
// → 이것은 Member.create()의 책임. MemberTest에서 검증해야 함
```

`MemberService.register()`의 책임은 **"중복이면 거절, 아니면 도메인 객체에게 생성을 위임하고
저장한다"**는 흐름 제어다. 필드 매핑·해싱·검증은 `Member.create()`의 책임이고, 그 검증의 자리는
`MemberTest`다.

이 구분을 놓치면 같은 로직이 두 테스트에서 검증되어, 변경 시 두 곳이 동시에 깨진다 (test
duplication). 각 테스트가 자기 책임만 검증하면 변경의 영향이 한 테스트에만 닿는다.

### 인자 내용 검증은 `ArgumentCaptor`로 — 직접 비교는 위험

`verify`에 객체를 직접 넘기면 Mockito는 `equals()`로 비교한다. JPA 엔티티는 보통 ID 기반
`equals`를 구현하는데, 저장 직전이라 ID가 둘 다 null이면 우연히 통과한다. 다른 데이터로 호출
해도 통과해버려 잘못된 검증이 된다.

```java
// ❌ ID가 둘 다 null이면 우연히 통과
Member expected = Member.create(...);
verify(repo).save(expected);

// ✅ 필드를 직접 검증
ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
verify(repo).save(captor.capture());
assertThat(captor.getValue().getLoginId()).isEqualTo("testId");
```

단, 위 항목과 함께 본다 — **그 검증이 정말 이 테스트의 책임 영역인가**를 먼저 따진다.
도메인 객체의 필드 매핑이 관심사라면 도메인 테스트로 옮긴다.

## 8. 검증할 가치가 없는 테스트는 작성하지 않는다

언어 차원에서 보장되는 사실을 런타임 테스트로 다시 확인하는 건 중복이다.

- **불변 VO의 불변성 테스트** ❌ — `final` 필드와 새 객체 반환 패턴이 컴파일러 차원에서 보장
- **검증 후 변경 패턴에서 "예외 시 상태 보존" 테스트** ❌ — 코드 한 번만 봐도 자명
- **도메인 예외 전파 테스트** ❌ — 자바 언어가 보장 (위 7번 참고)

이런 테스트는 미래의 잘못된 리팩토링을 잡을 수도 있지만, 그런 리팩토링은 보통 다른 정상 테스트가 같이 잡아준다.

## 9. 메서드 명명

테스트 메서드 이름은 검증 내용을 드러낸다. `@DisplayName`과 함께 한국어 설명을 단다.

```java
// 패턴: <결과>_when<조건>
void increasesBalance_whenAmountIsPositive()
void throwsException_whenAmountExceedsBalance()
void throwsNotFound_whenPointDoesNotExist()

// 또는 단순 행위명 (작은 그룹 안에서)
void createInitialPoint()
void create_null()
```

`@DisplayName`은 한국어로 비즈니스 의미를 적어 출력 가독성을 높인다.

## 10. 경계값과 분기 커버리지

행위의 계약(contract)을 검증할 때 다음을 모두 고려한다.

- **정상 케이스**: 일반적인 유효 입력
- **경계값**: 검증 조건의 양쪽 끝 (`amount == 0`, `amount == balance` 등)
- **위반 케이스**: 각 검증 규칙별로 한 개씩

`use`의 예시:

| 입력 | 검증 |
|---|---|
| `0 < amount < balance` | 정상: 잔액 감소 |
| `amount == 0` | 경계: 잔액 변화 없음 |
| `amount == balance` | 경계: 잔액 0 |
| `amount > balance` | 위반: `CONFLICT` 예외 |
| `amount < 0` | 위반: `Money` 단계에서 `BAD_REQUEST` |
