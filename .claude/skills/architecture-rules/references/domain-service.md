# 도메인 서비스 컨벤션

도메인 레이어에 속하는 `Service` 클래스 작성 가이드.

도메인 객체 단독으로 처리하기 어려운 도메인 로직(리포지토리 협력, 도메인 객체의 라이프사이클 관리 등)을 담당한다. 여러 도메인을 조합한 유스케이스나 트랜잭션 경계 같은 책임은 별도의 application 레이어에서 담당한다 (이 문서 범위 밖).

```
com.loopers.domain.point.PointService          ← 이 문서가 다루는 영역
com.loopers.application.point.XxxUseCase       ← 별도 영역
```

## 1. 책임

| 계층 | 책임 |
|---|---|
| 도메인 객체 (`Point`, `Money`) | 비즈니스 규칙, 상태 변경, 자기 정합성 검증 |
| 도메인 서비스 (`PointService`) | 도메인 객체 + 리포지토리 협력, 도메인 객체 라이프사이클(생성/조회/저장) |

도메인 서비스는 도메인 객체의 규칙을 다시 검증하지 않는다. 도메인 객체의 메서드(`Point.charge`)가 던지는 예외는 그대로 위로 전파한다.

```java
// ✅ 도메인 서비스는 도메인 객체를 신뢰
public ChargeQuery charge(ChargeCommand command) {
    Point point = pointRepository.findByMemberId(command.memberId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "..."));

    point.charge(command.amount());  // 검증 + 변경은 도메인 객체 책임

    return new ChargeQuery(command.memberId(), point.getBalance().getAmount());
}
```

## 2. CQS와 응답

도메인 메서드는 CQS에 따라 명령은 `void`로, 조회는 별도 메서드(또는 게터)로 분리한다. 하지만 도메인 서비스의 메서드가 명령 후 결과를 반환하는 건 자연스러운 일이다 (호출 측의 추가 호출을 줄임).

**CQS는 한 메서드 단위의 원칙이지, 전체 흐름의 원칙이 아니다.** 도메인 서비스는 도메인 객체의 명령과 조회를 조합해 응답을 만든다.

```java
public ChargeQuery charge(ChargeCommand command) {
    Point point = pointRepository.findByMemberId(command.memberId())
            .orElseThrow(...);

    point.charge(command.amount());  // 도메인은 void (CQS)

    // 도메인 서비스가 명령 + 조회를 조합해 응답 생성
    return new ChargeQuery(command.memberId(), point.getBalance().getAmount());
}
```

## 3. 입출력 DTO 정책

### 입력은 항상 `Command`

도메인 서비스에 들어오는 입력은 모두 `Command`로 정의한다. 호출 측이 임의의 파라미터 조합으로 호출하지 않도록 명시적인 입력 타입을 강제한다.

```java
public record CreateInitialCommand(Long memberId) { }
public record ChargeCommand(Long memberId, Money amount) { }
```

### 출력은 도메인 객체가 기본, 필요 시 `Query`

응답은 **도메인 객체를 직접 반환**하는 것이 기본이다. 다음 경우에만 `Query` 타입으로 변환해 반환한다:

1. 도메인이 변경될 소지가 있어 외부에 그대로 노출하는 게 부담스러울 때
2. 도메인을 그대로 반환하려면 불필요한 변환/조립 로직이 필요할 때

```java
// ✅ 도메인 그대로 반환
public Point createInitialPoint(CreateInitialCommand command) {
    // ...
    return pointRepository.save(Point.createInitial(command));
}

// ✅ Query로 변환 — 외부에 노출하기 부담스러운 형태일 때
public ChargeQuery charge(ChargeCommand command) {
    // ...
    return new ChargeQuery(command.memberId(), point.getBalance().getAmount());
}
```

`Query`를 반사적으로 만들면 변환 코드가 늘고 도메인 변경에 취약해진다. 노출에 문제가 없다면 도메인 객체를 그대로 반환한다.

### Command/Query는 한 클래스에 모아둔다

도메인 단위로 DTO 클래스(`PointServiceDto`)를 두고, 안에 record로 선언한다.

```java
public class PointServiceDto {
    public record CreateInitialCommand(Long memberId) { }
    public record ChargeCommand(Long memberId, Money amount) { }
    public record ChargeQuery(Long memberId, Long balance) { }
}
```

## 4. 멱등성: 의도가 메서드 이름에 드러나야 한다

서비스 메서드의 멱등 여부는 메서드 이름과 일치해야 한다.

```java
// 비멱등: "처음 생성"은 한 번만 일어나야 함
public Point createInitialPoint(CreateInitialCommand command) {
    if (pointRepository.findByMemberId(command.memberId()).isPresent()) {
        throw new CoreException(ErrorType.CONFLICT,
                "이미 Point가 존재합니다. memberId=" + command.memberId());
    }
    return pointRepository.save(Point.createInitial(command));
}
```

`orElseGet`으로 "있으면 가져오고 없으면 생성"하는 패턴은 두 의도를 한 메서드에 섞은 것이다. 의도가 다르면 메서드를 분리한다.

```java
// 멱등이 필요하면 별도 메서드로
public Point getOrCreatePoint(Long memberId) {
    return pointRepository.findByMemberId(memberId)
            .orElseGet(() -> pointRepository.save(...));
}
```

`createInitial`이라는 이름은 "처음 생성"을 의미하므로, 두 번째 호출은 비정상 상황이다. 조용히 넘어가지 않고 `CONFLICT`로 알린다.

## 5. ErrorType은 상황에 맞게 선택

`ErrorType`은 **레이어가 아니라 상황**에 따라 선택한다. 도메인 서비스라서 특별한 ErrorType이 추가되는 게 아니다.

| 상황 | ErrorType |
|---|---|
| 입력값 자체가 규칙 위반 (음수, 0 등) | `BAD_REQUEST` |
| 입력은 유효하나 현재 상태와 충돌 (잔액 부족, 중복 등) | `CONFLICT` |
| 조회 대상이 없음 | `NOT_FOUND` |

```java
// 조회 대상 없음 → NOT_FOUND
.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

// 이미 존재하는데 또 만들려 함 → CONFLICT (입력은 유효하나 상태와 충돌)
throw new CoreException(ErrorType.CONFLICT, "이미 Point가 존재합니다. memberId=" + command.memberId());
```

`BAD_REQUEST` vs `CONFLICT` 구분 원칙은 [domain-modeling.md](./domain-modeling.md) "예외 타입 구분" 참고.

## 6. 어노테이션과 의존성 주입

```java
@RequiredArgsConstructor
@Service
public class PointService {
    private final PointRepository pointRepository;
}
```

- `@Service`: Spring 빈 등록
- `@RequiredArgsConstructor`: `final` 필드를 받는 생성자 자동 생성 (생성자 주입)
- 필드는 `final`로 선언해 불변 보장 및 누락 시 컴파일 에러

`@Autowired`나 필드 주입은 사용하지 않는다 (테스트 용이성 + 의존 명시).

## 7. 트랜잭션 경계

쓰기 `@Transactional` 은 **기본이 application** 이다. 단일 애그리거트의 단순 작업일 때만
도메인 서비스에 두는 것을 허용한다. 읽기 메서드에는 `@Transactional(readOnly = true)` 를 선언할 수 있다.

확정 규칙은 `SKILL.md` 의 "트랜잭션 경계" 절에 있다. 여기와 어긋나면 그쪽이 우선이다.

## 8. 단위 테스트 전략

도메인 서비스의 단위 테스트는 Mockito 기반으로 작성한다. 리포지토리는 `@Mock`으로 가짜를 만들고, 서비스에는 **`@BeforeEach`에서 수동 생성자 주입**한다.

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

### 왜 `@InjectMocks`가 아닌 수동 주입인가

**의존성이 추가되었을 때 컴파일 단계에서 알아야 하기 때문**이다.

`@InjectMocks`는 리플렉션으로 주입하므로, 서비스에 새 의존성이 추가되어도 테스트 코드는 컴파일 통과한다. 실행 시점에서야 NPE 등으로 발견된다.

```java
// 서비스에 새 의존성 추가
@Service
public class PointService {
    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;  // ← 추가
}

// 수동 주입 → 모든 테스트에서 즉시 컴파일 에러
pointService = new PointService(pointRepository);
//                                              ^ 컴파일 에러: 인자 부족

// @InjectMocks → 컴파일은 통과, 실행 시점에 NPE
@InjectMocks
private PointService pointService;
```

수동 주입은 새 의존성을 **즉시, 그리고 모든 테스트에서** 인지하게 만든다. 이는 단순한 스타일 선호가 아니라 정합성 보장 메커니즘이다.

상세 패턴은 [testing.md](./testing.md) "Mock 기반 서비스 테스트" 참고.

## 9. 금지 사항

- 도메인 서비스에서 도메인 객체의 규칙을 다시 검증 (예: 충전 금액이 양수인지)
- 도메인 서비스에서 도메인 예외를 잡아 다른 ErrorType으로 변환 (의도가 있을 때만, 그것도 신중히)
- 메서드 이름과 동작의 멱등성이 어긋남 (`createInitial`인데 멱등이거나, `getOrCreate`인데 비멱등)
- 필드 주입 (`@Autowired private PointRepository ...`) 사용
- 테스트에서 `@InjectMocks` 사용 (수동 생성자 주입을 원칙으로)
