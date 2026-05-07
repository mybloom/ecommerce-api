# Entity / VO 테스트 가이드

도메인 객체(Entity, VO)는 Spring 컨텍스트 없이 순수 JUnit으로 테스트한다.  
테스트 대상은 두 가지로 나뉜다: **VO (Value Object)** 와 **Entity**.

---

## 1. VO 코드 작성 규칙

VO는 불변(immutable)이며, 동등성(equality)은 값으로 판단한다.

### 1-1. 클래스 구조

```java
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)   // 내부에서만 new Money(amount) 호출
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true) // JPA용
@EqualsAndHashCode                                  // 값 동등성
@Embeddable
public class Money {
    private final Long amount;
    // ...
}
```

- `@EqualsAndHashCode`: 값이 같으면 동일한 객체로 취급
- `@NoArgsConstructor(PROTECTED, force = true)`: JPA 프록시 생성 전용; 외부 직접 호출 차단
- `@AllArgsConstructor(PRIVATE)`: `of()` 팩토리 내부에서만 사용
- 필드는 `final` — 생성 후 변경 불가

### 1-2. 생성 팩토리 `of()`

검증을 모두 마친 뒤 인스턴스를 반환한다.

```java
public static Money of(Long amount) {
    if (amount == null) {
        throw new CoreException(ErrorType.BAD_REQUEST, "금액은 빈 값이 될 수 없습니다.");
    }
    if (amount < 0) {
        throw new CoreException(ErrorType.BAD_REQUEST, "금액은 음수가 될 수 없습니다.");
    }
    return new Money(amount);
}
```

- 입력값 자체가 잘못됐으면 → `BAD_REQUEST`
- `null` 체크는 `CoreException(BAD_REQUEST)` 또는 `requireNonNull()`

### 1-3. 편의 상수

자주 쓰이는 기본값은 상수로 선언한다.

```java
public static final Money ZERO = new Money(0L);
```

### 1-4. 조회 메서드 (쿼리)

상태를 변경하지 않고 boolean을 반환한다.

```java
public boolean isZero()     { return amount == 0L; }
public boolean isPositive() { return amount > 0; }

public boolean isLessThan(Money other) {
    requireNonNull(other, "비교할 금액은 필수입니다.");
    return this.amount < other.amount;
}
```

### 1-5. 산술 메서드

`Math.addExact` / `Math.subtractExact`를 사용해 오버플로를 감지하고, 새 인스턴스를 반환한다.

```java
public Money add(Money other) {
    requireNonNull(other, "더할 금액은 필수입니다.");
    return Money.of(Math.addExact(this.amount, other.amount));
}

public Money subtract(Money other) {
    requireNonNull(other, "뺄 금액은 필수입니다.");
    return Money.of(Math.subtractExact(this.amount, other.amount));  // 결과 음수 → of()가 BAD_REQUEST
}
```

---

## 2. VO 테스트 작성 규칙

### 2-1. 테스트 구조

`@Nested` + `@DisplayName`으로 메서드별로 그룹화한다.

```java
class MoneyTest {

    @Nested
    @DisplayName("of() 생성 검증")
    class Create { ... }

    @Nested
    @DisplayName("add")
    class Add { ... }

    @Nested
    @DisplayName("동등성")
    class Equality { ... }
}
```

### 2-2. `of()` — 정상 생성 (경계값 포함)

`@ParameterizedTest`로 경계값(0, 양수, MAX)을 한 번에 검증한다.

```java
@DisplayName("0 이상의 값으로 생성할 수 있다")
@ParameterizedTest
@ValueSource(longs = {0L, 1L, Long.MAX_VALUE})
void create_normal(long amount) {
    Money money = Money.of(amount);

    assertThat(money.getAmount()).isEqualTo(amount);
}
```

### 2-3. `of()` — 입력 무효화 예외 (BAD_REQUEST)

예외는 반드시 `ErrorType`으로 단언한다 (메시지 문자열 비교 금지).

```java
@Test
@DisplayName("amount가 null이면 BAD_REQUEST 예외가 발생한다")
void create_null() {
    assertThatThrownBy(() -> Money.of(null))
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
}

@Test
@DisplayName("amount가 음수면 BAD_REQUEST 예외가 발생한다")
void create_negative() {
    assertThatThrownBy(() -> Money.of(-1L))
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
}
```

### 2-4. 동등성 검증 (equals + hashCode)

같은 값 → 동등, 다른 값 → 비동등, 상수 비교까지 작성한다.

```java
@Test
@DisplayName("같은 금액의 Money는 동등하고 hashCode도 같다")
void equal_same_amount() {
    Money money1 = Money.of(1000L);
    Money money2 = Money.of(1000L);

    assertAll(
            () -> assertThat(money1).isEqualTo(money2),
            () -> assertThat(money1.hashCode()).isEqualTo(money2.hashCode())
    );
}

@Test
@DisplayName("Money.ZERO와 Money.of(0L)은 동등하다")
void zero_equals_of_zero() {
    assertThat(Money.ZERO).isEqualTo(Money.of(0L));
}
```

### 2-5. 산술 메서드 — 정상 케이스

결과값은 새 인스턴스이므로 `isEqualTo()`로 값 비교한다.

```java
@Test
@DisplayName("두 금액을 더한 결과를 반환한다")
void returnsSum() {
    Money result = Money.of(1_000L).add(Money.of(500L));

    assertThat(result).isEqualTo(Money.of(1_500L));
}

@Test
@DisplayName("0을 더하면 자신과 같은 값을 반환한다")
void returnsSameAmount_whenAddingZero() {
    Money result = Money.of(1_000L).add(Money.ZERO);

    assertThat(result).isEqualTo(Money.of(1_000L));
}
```

### 2-6. 산술 메서드 — 오버플로/음수 결과 예외

```java
@Test
@DisplayName("결과가 long 범위를 초과하면 ArithmeticException이 발생한다")
void throwsException_whenResultOverflows() {
    Money max = Money.of(Long.MAX_VALUE);

    assertThatThrownBy(() -> max.add(Money.of(1L)))
            .isInstanceOf(ArithmeticException.class);
}

@Test
@DisplayName("결과가 음수가 되면 BAD_REQUEST 예외가 발생한다")
void throwsException_whenResultIsNegative() {
    assertThatThrownBy(() -> Money.of(100L).subtract(Money.of(200L)))
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
}
```

### 2-7. 조회 메서드 (isZero, isPositive, isLessThan)

true/false 양쪽 케이스를 모두 작성한다.

```java
@Test
@DisplayName("금액이 0이면 true를 반환한다")
void isZero_true() {
    assertAll(
            () -> assertThat(Money.of(0L).isZero()).isTrue(),
            () -> assertThat(Money.ZERO.isZero()).isTrue()
    );
}

@Test
@DisplayName("금액이 0이 아니면 false를 반환한다")
void isZero_false() {
    assertThat(Money.of(1L).isZero()).isFalse();
}
```

---

## 3. Entity 코드 작성 규칙

### 3-1. 클래스 구조

```java
@Getter
@Entity
@Table(name = "point")
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 전용; 외부 호출 차단
public class Point {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Money balance;   // VO 임베드

    @Column(nullable = false, unique = true)
    private Long memberId;
    // ...
}
```

- `@NoArgsConstructor(PROTECTED)`: JPA와 테스트 내부 조작만 허용
- Spring 어노테이션 금지 (`@Service`, `@Component` 등)
- JPA 매핑 어노테이션(`@Entity`, `@Column` 등)은 허용

### 3-2. 정적 팩토리 (`create`, `createInitial`)

검증 후 필드를 채운 인스턴스를 반환한다. 필드는 팩토리 안에서만 설정한다.

```java
public static Point createInitial(PointServiceDto.CreateInitialCommand command) {
    Point point = new Point();
    point.balance = Money.of(INITIAL_POINT_AMOUNT);
    point.memberId = requireNonNull(command.memberId());
    return point;
}
```

- 필수 값 → `requireNonNull()` 또는 VO 팩토리에서 검증
- 초기화 순서: 모든 필드를 설정한 뒤 반환 (부분 상태 금지)

### 3-3. 커맨드 메서드 (상태 변경)

반환값은 `void`. 검증 → 변경 순서를 지킨다.

```java
public void charge(Money amount) {
    if (!amount.isPositive()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "충전 금액은 1 이상이어야 합니다.");
    }
    this.balance = this.balance.add(amount);    // 검증 통과 후 변경
}

public void use(Money amount) {
    if (this.balance.isLessThan(amount)) {
        throw new CoreException(ErrorType.CONFLICT, "잔액이 부족합니다.");
    }
    this.balance = this.balance.subtract(amount);
}
```

- 입력 자체가 잘못됐으면 → `BAD_REQUEST`
- 입력은 유효하지만 현재 상태와 충돌하면 → `CONFLICT`

### 3-4. VO를 필드로 사용할 때

VO를 직접 `@Embedded`로 포함하고, 산술은 VO에 위임한다.

```java
@Embedded
private Money balance;

// Entity 메서드에서
this.balance = this.balance.add(amount);  // Money.add()에 위임
```

---

## 4. Entity 테스트 작성 규칙

### 4-1. Fixture 클래스

테스트마다 반복되는 객체 생성은 Fixture로 추출한다.  
팩토리 메서드 이름은 `a-` / `an-` 접두사를 쓴다.

```java
// PointFixture.java
public class PointFixture {
    public static final long DEFAULT_MEMBER_ID = 1L;

    public static Point anInitialPoint() {
        return Point.createInitial(new PointServiceDto.CreateInitialCommand(DEFAULT_MEMBER_ID));
    }

    public static Point aPointWithBalance(long balance) {
        Point point = anInitialPoint();
        point.charge(Money.of(balance));
        return point;
    }

    public static PointServiceDto.CreateInitialCommand anInitialCommand(Long memberId) {
        return new PointServiceDto.CreateInitialCommand(memberId);
    }
}
```

- `DEFAULT_*` 상수를 Fixture에 선언하고 테스트 단언에 재사용한다
- 기본값 → `anInitialPoint()`, 상태 지정 → `aPointWithBalance(1000L)`

### 4-2. 팩토리 메서드 — 초기 상태 검증

생성 직후 필드 값이 의도한 초기값인지 확인한다.

```java
@Test
@DisplayName("Point 생성 시, 초기값이 0으로 생성된다.")
void createInitialPoint() {
    Point point = Point.createInitial(anInitialCommand());

    assertAll(
            () -> assertThat(point.getMemberId()).isEqualTo(DEFAULT_MEMBER_ID),
            () -> assertThat(point.getBalance().isZero()).isTrue()
    );
}
```

- `assertAll()`로 여러 필드를 한 번에 검증 (첫 번째 실패에서 멈추지 않음)
- 단언은 Fixture 상수(`DEFAULT_MEMBER_ID`)와 의미 있는 조건(`isZero()`)으로 작성

### 4-3. 팩토리 메서드 — null 가드

`requireNonNull()`이 걸린 필수 파라미터는 NPE가 발생하는지 확인한다.

```java
@DisplayName("memberId가 null이면 NPE 예외가 발생한다")
@Test
void throwsException_whenMemberIdIsNull() {
    assertThatThrownBy(() -> Point.createInitial(anInitialCommand(null)))
            .isInstanceOf(NullPointerException.class);
}
```

### 4-4. 커맨드 메서드 — 정상 케이스

호출 전후 상태 변화를 검증한다. 초기 상태는 Fixture로 준비한다.

```java
@Test
@DisplayName("양수 금액을 충전하면 잔액이 증가한다")
void increasesBalance_whenAmountIsPositive() {
    Point point = anInitialPoint();           // 잔액 0

    point.charge(Money.of(1_000L));

    assertThat(point.getBalance()).isEqualTo(Money.of(1_000L));
}

@Test
@DisplayName("보유 잔액보다 적은 금액을 차감하면 잔액이 감소한다")
void decreasesBalance_whenAmountIsLessThanBalance() {
    Point point = aPointWithBalance(1_000L);  // 잔액 1000

    point.use(Money.of(300L));

    assertThat(point.getBalance()).isEqualTo(Money.of(700L));
}
```

### 4-5. 커맨드 메서드 — BAD_REQUEST 예외 (입력 무효화)

```java
@Test
@DisplayName("0을 충전하면 BAD_REQUEST 예외가 발생한다")
void throwsException_whenAmountIsZero() {
    Point point = anInitialPoint();

    assertThatThrownBy(() -> point.charge(Money.of(0L)))
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
}
```

### 4-6. 커맨드 메서드 — CONFLICT 예외 (상태 충돌)

현재 상태(잔액 부족 등)와 충돌하는 시나리오를 검증한다.

```java
@Test
@DisplayName("보유 잔액보다 큰 금액을 차감하면 CONFLICT 예외가 발생한다")
void throwsException_whenAmountExceedsBalance() {
    Point point = aPointWithBalance(1_000L);

    assertThatThrownBy(() -> point.use(Money.of(2_000L)))
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
}
```

### 4-7. 커맨드 메서드 — 경계 케이스

딱 잔액과 같은 금액, 0, MAX 등 경계를 명시적으로 작성한다.

```java
@Test
@DisplayName("보유 잔액과 같은 금액을 차감하면 잔액이 0이 된다")
void becomesZero_whenAmountEqualsBalance() {
    Point point = aPointWithBalance(1_000L);

    point.use(Money.of(1_000L));

    assertThat(point.getBalance().isZero()).isTrue();
}

@Test
@DisplayName("0을 차감하면 잔액이 변하지 않는다")
void keepsBalance_whenAmountIsZero() {
    long initialBalance = 1_000L;
    Point point = aPointWithBalance(initialBalance);

    point.use(Money.of(0L));

    assertThat(point.getBalance()).isEqualTo(Money.of(initialBalance));
}
```

### 4-8. 도메인 메서드 위임 검증 (PasswordEncoder 같은 협력 객체)

인터페이스 구현체를 테스트 내부에서 익명 클래스로 정의한다. Mock 라이브러리 없이 작성 가능하다.

```java
private static final PasswordEncoder PASSWORD_ENCODER = new PasswordEncoder() {
    @Override
    public String encode(String password) { return password.toUpperCase(); }

    @Override
    public boolean matches(String password, String passwordHash) {
        return encode(password).equals(passwordHash);
    }
};

@Test
void verifyPassword() {
    assertThat(member.verifyPassword(DEFAULT_PASSWORD, PASSWORD_ENCODER)).isTrue();
    assertThat(member.verifyPassword(DEFAULT_PASSWORD + "_", PASSWORD_ENCODER)).isFalse();
}
```

---

## 5. 테스트 작성 체크리스트

| 항목 | VO | Entity |
|---|:---:|:---:|
| `of()` / 팩토리 — 정상 생성 | ✅ | ✅ |
| `of()` / 팩토리 — null 입력 → NPE 또는 BAD_REQUEST | ✅ | ✅ |
| `of()` / 팩토리 — 무효 입력 → BAD_REQUEST | ✅ | - |
| 초기 상태 필드값 검증 | - | ✅ |
| 동등성 (equals + hashCode) | ✅ | - |
| 커맨드 메서드 — 정상 케이스 (상태 변화 확인) | - | ✅ |
| 커맨드 메서드 — BAD_REQUEST | - | ✅ |
| 커맨드 메서드 — CONFLICT | - | ✅ |
| 커맨드 메서드 — 경계 케이스 | ✅ | ✅ |
| 산술 오버플로 | ✅ | - |
| 예외 단언은 `ErrorType` 으로 | ✅ | ✅ |
| Spring 컨텍스트 없는 순수 JUnit | ✅ | ✅ |
