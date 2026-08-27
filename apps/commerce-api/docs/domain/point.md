# 포인트 도메인

회원이 보유한 적립금. 충전과 차감이 가능하며, 잔액은 음수가 될 수 없다.

## 모델

### Point (엔티티)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | `Long` | PK, IDENTITY 전략 |
| `memberId` | `Long` | 소유자 식별자, unique |
| `balance` | `Money` | 현재 잔액 (`@Embeddable`) |

### Money (VO)

`Long amount`를 감싸는 불변 객체. 0 이상의 값만 허용한다. 자세한 설계 원칙은 @docs/conventions/domain-modeling.md 참고.

## 도메인 객체 비즈니스 규칙

### 생성 (`Point.createInitial`)

- `memberId`는 필수 (null 불가)
- 초기 잔액은 0

### 충전 (`Point.charge`)

- `amount`는 1 이상 (양수)
- 충전 후 잔액은 기존 잔액 + amount
- `amount`가 0 이하 → `BAD_REQUEST`
- `amount`가 음수인 경우는 `Money.of` 단계에서 이미 차단됨

### 차감 (`Point.use`)

- `amount`는 0 이상 (`Money` VO가 보장)
- 보유 잔액 이하만 차감 가능
- 차감 후 잔액은 기존 잔액 - amount
- `amount`가 잔액 초과 → `CONFLICT`
- `amount`가 0이면 잔액 변화 없음 (정상 처리)

## 도메인 서비스 (`PointService`)

도메인 레이어에 속한다. 도메인 객체와 리포지토리의 협력을 담당. 자세한 작성 원칙은 @docs/conventions/domain-service.md 참고.

### `createInitialPoint(CreateInitialCommand) → Point`

회원의 최초 Point를 생성한다. **비멱등** 메서드.

- 이미 해당 `memberId`의 Point가 존재하면 → `CONFLICT`
- 그렇지 않으면 잔액 0인 Point를 생성하여 저장
- **반환 타입은 도메인 객체 `Point`** — 외부 노출에 부담이 없으므로 별도 Query로 변환하지 않음

```java
public Point createInitialPoint(CreateInitialCommand command) {
    if (pointRepository.findByMemberId(command.memberId()).isPresent()) {
        throw new CoreException(ErrorType.CONFLICT,
                "이미 Point가 존재합니다. memberId=" + command.memberId());
    }
    return pointRepository.save(Point.createInitial(command));
}
```

"처음 생성"이라는 의도상 두 번째 호출은 비정상 상황으로 간주한다.

### `charge(ChargeCommand) → ChargeQuery`

기존 Point에 금액을 충전하고 결과를 반환한다.

- Point 조회 실패 → `NOT_FOUND`
- 충전 검증은 도메인(`Point.charge`)에 위임 → 실패 시 도메인 예외 전파
- **반환 타입은 `ChargeQuery`** — Point 전체가 아니라 `(memberId, balance)`만 필요해서, Point 그대로 반환하면 호출 측이 불필요한 변환을 해야 함

```java
public ChargeQuery charge(ChargeCommand command) {
    Point point = pointRepository.findByMemberId(command.memberId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

    point.charge(command.amount());

    return new ChargeQuery(command.memberId(), point.getBalance().getAmount());
}
```

## 예외 정책

| 상황 | ErrorType | 발생 위치 |
|---|---|---|
| 충전 금액이 0 이하 | `BAD_REQUEST` | `Point.charge` |
| 차감 금액이 잔액 초과 | `CONFLICT` | `Point.use` |
| 음수 금액 (충전/차감) | `BAD_REQUEST` | `Money.of` |
| `null` 금액 | `BAD_REQUEST` | `Money.of` |
| 충전 시 Point 미존재 | `NOT_FOUND` | `PointService.charge` |
| 초기 Point 중복 생성 | `CONFLICT` | `PointService.createInitialPoint` |

`BAD_REQUEST` vs `CONFLICT` 구분 원칙은 @docs/conventions/domain-modeling.md "예외 타입 구분" 참고.

## DTO

```java
public class PointServiceDto {
    public record CreateInitialCommand(Long memberId) { }
    public record ChargeCommand(Long memberId, Money amount) { }
    public record ChargeQuery(Long memberId, Long balance) { }
}
```

- **Command**: 도메인 서비스로 들어오는 입력 (항상 사용)
- **Query**: 도메인 그대로 반환하기 부담스럽거나 불필요한 변환 로직이 필요할 때 사용

## 검증 책임 분배

`Money` VO가 **금액 자체에 대한 검증**(null, 음수)을 담당한다.
`Point`는 `Money`를 신뢰하고 **포인트 도메인 고유의 규칙**(충전 금액 ≥ 1, 잔액 ≥ 차감 금액)만 검증한다.
`PointService`는 도메인 객체의 규칙을 다시 검증하지 않고, **도메인 객체 라이프사이클에 관한 규칙**(존재 여부, 중복 생성 등)만 검증한다.

```java
// Money: 금액의 유효성
Money.of(-1L);  // → BAD_REQUEST

// Point: 도메인 정책
point.charge(Money.of(0L));   // → BAD_REQUEST (0은 충전 불가)
point.use(Money.of(99999L));  // → CONFLICT (잔액 부족)

// PointService: 도메인 객체 라이프사이클
pointService.charge(...);  // Point 미존재 → NOT_FOUND
pointService.createInitialPoint(...);  // 이미 존재 → CONFLICT
```

## 정체성

`Point`는 자기 `memberId`를 알고 있으므로 도메인 메서드(`charge`, `use`)는 `memberId`를 인자로 받지 않는다. "누구의 Point인가"는 저장소 조회 시점에 해결되며, 인가 검증은 상위 레이어의 책임이다.

## 테스트

- `PointTest`: 도메인 행위 검증 (충전/차감 규칙, 경계값)
- `MoneyTest`: VO 검증 (생성, 연산, 오버플로우)
- `PointServiceTest`: 도메인 서비스 행위 검증 (Mockito 기반, 리포지토리 격리)
- `PointFixture`: 테스트 픽스처 (`anInitialPoint`, `aPointWithBalance`, `anInitialCommand`, `DEFAULT_MEMBER_ID`)

테스트 작성 패턴은 @docs/conventions/testing.md 참고.
