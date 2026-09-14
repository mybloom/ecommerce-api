# 도메인 모델링 컨벤션

도메인 객체(엔티티, VO)를 작성할 때 따르는 원칙. 코드 일관성과 도메인 의도를 드러내기 위한 가이드.

## 1. 엔티티 vs VO

| 구분 | 엔티티 | VO |
|---|---|---|
| 정체성 | ID로 식별 | 값으로 식별 |
| 가변성 | mutable (상태가 변함) | immutable (불변) |
| 예시 | `Point`, `Member` | `Money` |

엔티티는 자기 식별자(`memberId`, `id`)를 갖고, VO는 값 자체로 의미가 결정된다.

## 2. VO 작성 규칙

### 불변성 보장

- 모든 필드는 `final`
- setter 금지 (`@Setter` 사용 금지)
- 모든 연산은 새 객체를 반환 (자기 자신을 변경하지 않음)

### 검증은 생성 시점에 모두 수행

VO는 **생성된 시점부터 유효한 값임을 보장**해야 한다. 사용처에서 다시 검증할 필요가 없도록 `of` 정적 팩토리에서 모든 검증을 처리한다.

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

이 원칙의 결과로, 다른 도메인 객체에서 `Money`를 받을 때 `null` 체크나 음수 체크를 다시 할 필요가 없다. 중복 검증은 제거한다.

```java
// ❌ 중복 검증
public void use(Money amount) {
    if (amount == null) throw ...;       // Money.of에서 이미 검증됨
    if (amount.isNegative()) throw ...;  // Money.of에서 이미 검증됨
    // ...
}

// ✅ Money를 신뢰
public void use(Money amount) {
    if (this.balance.isLessThan(amount)) throw ...;
    // ...
}
```

### 산술 연산은 오버플로우 방어

`+`, `-` 대신 `Math.addExact`, `Math.subtractExact`를 사용한다. silent wrap-around로 인한 사일런트 버그를 방지하기 위함이다.

```java
public Money add(Money other) {
    return Money.of(Math.addExact(this.amount, other.amount));
}

public Money subtract(Money other) {
    return Money.of(Math.subtractExact(this.amount, other.amount));
}
```

`Long.MAX_VALUE` 근처에서 `+`는 음수로 wrap-around되지만, `Math.addExact`는 `ArithmeticException`을 던져 즉시 실패한다.

### 어노테이션

```java
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)  // JPA용
@EqualsAndHashCode
@Embeddable
public class Money { ... }
```

- `@AllArgsConstructor(PRIVATE)`: 생성은 `of`로만
- `@NoArgsConstructor(PROTECTED, force = true)`: JPA의 reflection 생성용 (final 필드를 위해 force 필요)
- `@EqualsAndHashCode`: 값 동등성

### VO를 엔티티에 심을 때는 `@AttributeOverride`로 컬럼을 지정한다

**`@Embedded` 필드에 붙인 `@Column`은 JPA가 무시한다.** 컬럼명도 제약도 적용되지 않는다.

```java
// ❌ 통째로 무시된다
@Embedded
@Column(name = "email", nullable = false, unique = true)
private Email email;

// ✅
@Embedded
@AttributeOverride(name = "address", column = @Column(name = "email", nullable = false, unique = true))
private Email email;
```

`name`은 **VO 안의 필드명**(`Email.address`), `column`은 매핑할 **DB 컬럼**이다.
지정하지 않으면 VO의 필드명이 그대로 컬럼명이 되고(`address`), `unique = true`도 사라진다.
같은 VO를 여러 엔티티에 다른 컬럼명으로 심을 수 있는 것도 이 애노테이션 덕분이다
(`Money.amount` → `total_amount`, `unit_price`, `amount`).

> 실제로 `Member.email`이 `@Column`만 붙어 있어 컬럼이 `address`로 생성되고 UNIQUE 제약이
> 걸리지 않았다. 같은 이메일로 몇 번이든 가입되던 원인이다.

**매핑을 바꿨으면 생성된 DDL을 눈으로 확인한다.** 테스트 프로파일은 `ddl-auto: create`라
`create table` 로그가 남는다. 코드만 읽어서는 이런 누락이 보이지 않는다.

## 3. 엔티티 작성 규칙

### 정체성을 안다면 메서드 인자로 받지 않는다

엔티티는 자신이 누구의 것인지 안다. `Point`는 `memberId`를 필드로 갖고 있으므로, 도메인 메서드에서 `memberId`를 또 받지 않는다.

```java
// ❌ 자기 정체성을 외부에서 또 받음
point.charge(memberId, amount);

// ✅ 객체가 자기 자신을 앎
point.charge(amount);
```

"누구의 Point인가"는 저장소 조회 시점(`findByMemberId`)에 해결된다. 도메인 메서드는 그 이후에 호출되므로 다시 검증할 필요가 없다.

### 정적 팩토리로 생성

생성자를 `protected`로 막고, 의도가 드러나는 정적 팩토리 메서드로 생성한다.

```java
public static Point createInitial(PointServiceDto.CreateInitialCommand command) {
    Point point = new Point();
    point.balance = Money.of(INITIAL_POINT_AMOUNT);
    point.memberId = requireNonNull(command.memberId());
    return point;
}
```

이 시점에는 객체가 아직 없으므로 외부에서 정체성(`memberId`)을 받는 것이 자연스럽다.

### 명령은 void, 조회는 게터 또는 별도 메서드 (CQS)

상태를 변경하는 메서드(`charge`, `use`)는 `void`로 둔다. 변경 후 잔액이 필요하면 호출 측에서 게터로 조회한다.

```java
// ✅ 명령과 조회 분리
public void charge(Money amount) { ... }
public void use(Money amount) { ... }
// 조회는 @Getter가 생성한 getBalance() 사용
```

## 4. 예외 타입 구분

`CoreException(ErrorType, message)` 형태로 던진다. 메시지엔 디버깅을 위해 변수값을 포함한다.

```java
throw new CoreException(ErrorType.BAD_REQUEST,
        "충전 금액은 1 이상이어야 합니다. amount=" + amount);
```

### `BAD_REQUEST` vs `CONFLICT`

- **`BAD_REQUEST`**: 입력값 자체가 규칙 위반
  - 예: 음수 금액, 충전 금액이 0
- **`CONFLICT`**: 입력은 유효하지만 현재 상태와 충돌
  - 예: 잔액 부족 (입력값 자체는 유효한 양수)

```java
public void charge(Money amount) {
    if (!amount.isPositive()) {
        // 입력값이 잘못됨 → BAD_REQUEST
        throw new CoreException(ErrorType.BAD_REQUEST, "충전 금액은 1 이상이어야 합니다. amount=" + amount);
    }
    this.balance = this.balance.add(amount);
}

public void use(Money amount) {
    if (this.balance.isLessThan(amount)) {
        // 입력값은 유효하지만 현재 상태(잔액)와 충돌 → CONFLICT
        throw new CoreException(ErrorType.CONFLICT,
                "잔액이 부족합니다. balance=" + this.balance + ", amount=" + amount);
    }
    this.balance = this.balance.subtract(amount);
}
```

## 5. 검증 → 상태 변경 순서

도메인 메서드는 모든 검증을 통과한 후에 상태를 변경한다. 중간에 예외가 터져 객체가 partial state로 남는 일을 막기 위함이다.

```java
public void use(Money amount) {
    // 1. 검증 (실패 시 예외, 상태 안 바뀜)
    if (this.balance.isLessThan(amount)) {
        throw new CoreException(...);
    }

    // 2. 상태 변경 (검증 통과 후 한 번에)
    this.balance = this.balance.subtract(amount);
}
```

## 6. 금지 사항

- 엔티티에 `@Setter` 또는 public setter 추가
- 도메인 패키지에 Spring 어노테이션(`@Service`, `@Component` 등) 사용
  - JPA 매핑 어노테이션(`@Entity`, `@Embeddable` 등)은 허용
- VO에 mutable 컬렉션 필드 (사용 시 `Collections.unmodifiableList`로 감싸기)
- 산술 연산에 `+`, `-` 직접 사용 (대신 `Math.addExact`, `Math.subtractExact`)
