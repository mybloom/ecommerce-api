---
name: architecture-rules
description: com.loopers 패키지의 Spring Boot 이커머스 코드를 작성, 수정, 리뷰할 때 항상 참조. 레이어 의존성 방향, 패키지 구조, 예외 처리(CoreException/ErrorType), 네이밍 컨벤션, 참고 구현체 위치를 담음. 새 기능 추가, 기존 코드 수정, 코드 리뷰, 리팩토링 모든 작업에 적용됨.
---

# eCommerce API — 아키텍처 규칙

## 프로젝트 개요

- 멀티모듈 Gradle 프로젝트. 메인 개발은 `apps/commerce-api/`에서 진행
- DDD 레이어드 아키텍처 (interfaces → application → domain ← infrastructure)
- TFD(Test-First Development)로 개발: 레이어별로 테스트 → 최소 구현 → 다음 레이어 순서
- 베이스 패키지: `com.loopers`

## 의존성 방향 (절대 규칙)

```
interfaces.api  →  application  →  domain  ←  infrastructure
```

### 허용되는 의존
- `interfaces.api` → `application` (UseCase 호출)
- `application` → `domain` (도메인 객체/서비스/Repository 인터페이스 사용)
- `infrastructure` → `domain` (Repository 인터페이스 구현)

### 절대 금지
- ❌ `domain`이 `application` 또는 `interfaces` 레이어를 import
- ❌ `interfaces.api`가 `domain`을 직접 호출 (반드시 `application` 경유)
- ❌ `infrastructure`가 `application`이나 `interfaces`를 import
- ❌ 도메인 서비스가 **다른 도메인의 Repository**를 주입
- ❌ 도메인 서비스가 **다른 도메인의 Service**를 주입 (도메인 서비스 간 호출 금지)

위반 발견 시 작업을 중단하고 사용자에게 보고할 것.

## 패키지 구조와 네이밍

### `interfaces.api.{도메인}`
HTTP 진입점. Spring MVC 사용.
- `*V1Controller` — REST 컨트롤러 (`implements *V1ApiSpec`)
- `*V1ApiSpec` — API 명세 인터페이스 (Swagger/OpenAPI 어노테이션 분리용)
- `*V1Dto` — 요청/응답 DTO를 담는 outer 클래스
    - 내부 record: `*Request` (요청), `*Response` (응답)
    - `*Request`는 `toInfo()` 메서드로 `*UseCaseDto.*Info`로 변환
    - `*Response`는 정적 `from(*UseCaseDto.*Result)` 팩토리 메서드 보유
- 책임: HTTP ↔ DTO 변환만. 비즈니스 로직 금지.

### `application.{도메인}`
유스케이스 오케스트레이션. 트랜잭션 경계.
- `*UseCase` — 유스케이스 진입점 (`@Component` 사용)
- `*UseCaseDto` — UseCase 입출력 DTO를 담는 outer 클래스 (도메인 객체를 외부에 직접 노출하지 않음)
    - 입력 record: `*Info`
    - 출력 record: `*Result`
    - `*Info`는 `toCommand()` 메서드로 `*ServiceDto.*Command`로 변환
    - `*Result`는 정적 `from(*ServiceDto.*Query)` 팩토리 메서드 보유
- 책임: 트랜잭션 관리(`@Transactional`), 도메인 객체 조합, 외부 시스템 호출 조정.
- **여러 도메인 서비스의 오케스트레이션은 오직 여기서만 한다.** 도메인 서비스끼리 직접 호출하지 않는다.
- `*Processor` — 트랜잭션 경계가 UseCase와 갈라져야 할 때만 두는 보조 컴포넌트 (`@Component` + `@Transactional`)
    - 트랜잭션 안에서 잡을 수 없는 예외(예: DB 제약 위반)를 UseCase가 트랜잭션 **밖**에서 변환해야 할 때 사용
    - `*UseCase`는 트랜잭션 없이 `*Processor`를 감싸 예외를 변환하고, `*Processor`가 도메인 서비스를 조합
    - 그런 이유가 없으면 만들지 않는다. `*UseCase`에 `@Transactional`을 붙이는 것이 기본
    - 예: `application/productlike/ProductLikeProcessor.java`
- 비즈니스 규칙은 도메인에 위임. 여기서는 "흐름"만.

### `domain.{도메인}`
비즈니스 로직 + JPA 영속성.
- 도메인 엔티티 (예: `Member`, `Point`) — `@Entity`, `@Table` 적용, `BaseEntity` 상속 필수
- 값 객체 (예: `Email`) — `@Embeddable` 적용, 엔티티에 `@Embedded`로 포함
- `*Service` — 도메인 서비스 (`@Service` 사용, 단일 도메인 책임)
    - **자기 도메인의 Repository만 주입받는다.** 다른 도메인의 Repository도, 다른 도메인의 Service도 주입하지 않는다
    - 여러 도메인을 엮는 흐름은 **`application`의 UseCase만** 오케스트레이션한다
    - 즉 도메인 서비스는 서로를 모른다. 협력이 필요하면 UseCase가 각각을 호출해 조합한다
- `*Repository` — Repository 인터페이스 (구현 X, Spring 의존 없음)
- `*ServiceDto` — 도메인 서비스 입출력 DTO를 담는 outer 클래스
    - 입력 record: `*Command`
    - 출력 record: 도메인 객체 (기본), 단 Money 관련 응답 시 `*Query`
- 책임: 비즈니스 규칙, 불변식 검증.

### `domain.shared`
여러 도메인이 공유하는 값 객체.
- 예: `Money` (`@Embeddable` 적용, `domain.shared` 패키지에 위치)

### `infrastructure.{도메인}`
영속성 어댑터.
- `*RepositoryImpl` — `domain.*Repository` 구현체 (`@Repository` 사용)
- `*JpaRepository` — Spring Data JPA 인터페이스 (도메인 엔티티 클래스 직접 사용)
- 별도 `*Entity` 클래스 없음 — 도메인 엔티티가 JPA 엔티티를 겸함
- 책임: DB 접근 등 기술적 세부사항.

### `support.error`
공통 예외 처리 (전 레이어 공유).

## 예외 처리

- 비즈니스 규칙 위반은 항상 `CoreException` 사용
- 에러 종류는 `ErrorType` enum으로 관리 (HTTP 상태, 메시지 매핑)
- ❌ `IllegalArgumentException`, `RuntimeException` 등 표준 예외 직접 throw 금지
- ❌ 예외를 잡아서 무시(swallow)하는 빈 catch 블록 금지
- 새 에러 케이스 추가 시 `ErrorType` enum에 먼저 정의

```java
// 좋은 예 — ErrorType만
throw new CoreException(ErrorType.BAD_REQUEST, "충전 금액은 1 이상이어야 합니다. amount=" + amount);
```

## 도메인 엔티티 / 값 객체 작성 규칙

- `@NoArgsConstructor(access = PROTECTED)` 필수
- `@Setter` 금지
- enum 속성으로 의미를 명시한다

```java
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BrandStatus {
    ACTIVE("Active", "활성", "정상적으로 노출되는 상태"),
    INACTIVE("Inactive", "비활성", "일시적으로 노출이 중단된 상태"),
    WITHDRAWN("Withdrawn", "탈퇴", "영구적으로 종료되어 더 이상 사용되지 않는 상태");

    private final String code;
    private final String label;
    private final String description;
}
```

## Service 반환 타입 규칙

- **기본**: 도메인 객체를 직접 반환 (예: `Brand`, `Member`)
- **예외**: Money 관련 응답처럼 **계산 결과/집계값**을 담아야 할 때만
  `*ServiceDto.Query` 사용 (예: `Point` 잔액 조회 결과)


## 트랜잭션 경계

- 쓰기 `@Transactional`은 기본은 application, 단일 애그리거트 단순 작업은 domain service 허용
- 도메인 서비스의 읽기 메서드에는 `@Transactional(readOnly = true)` 선언 가능
- ❌ Controller에 `@Transactional` 금지


## 주석 정책

**이름이 말하는 것을 주석으로 반복하지 않는다.** 주석은 코드가 말할 수 없는 것 — **왜 그렇게 했는지**,
어떤 결정을 따랐는지, 무엇을 감수했는지 — 만 적는다.

```java
// ❌ 이름이 이미 말하고 있다
/** 재고 차감을 위해 비관적 락을 걸고 조회한다. */
Optional<Product> findByIdForUpdate(Long id);

// ✅ 이름으로 충분하다
Optional<Product> findByIdForUpdate(Long id);
```

- **Repository(인터페이스·JpaRepository·Impl)에는 주석을 달지 않는다.** 메서드명이 곧 쿼리라
  덧붙일 것이 없다. `Impl`은 위임뿐이라 더욱 그렇다
- 반대로 **결정 번호가 붙는 자리에는 남긴다.** 도메인 규칙, 트랜잭션 경계, 감수한 트레이드오프처럼
  코드만 봐서는 "왜"를 알 수 없는 곳이다

```java
// ✅ 코드가 말하지 못하는 것
/**
 * 재고 부족 검사와 차감을 함께 수행한다 — 그 사이 간극을 없애기 위함이다 (참고: Order-005).
 */
public void decreaseStock(int quantity) { ... }
```

## Lombok 사용 정책

- ✅ 허용: `@Getter`, `@RequiredArgsConstructor`, `@Builder`, `@NoArgsConstructor(access = PROTECTED)`, `@EqualsAndHashCode`
- ✅ 허용: `@AllArgsConstructor(access = PRIVATE)` — 값 객체의 private 생성자 전용
- ❌ 금지: `@Setter`, `@Data`, `@AllArgsConstructor` (public)

## 테스트 코드 컨벤션

### 의존성 주입
- 테스트 클래스도 **생성자 주입** 사용 (필드 주입 X, `@Autowired` 필드 X)

```java
private final MemberUseCase memberUseCase;
private final MemberRepository memberRepository;
private final PointRepository pointRepository;
private final DatabaseCleanUp databaseCleanUp;

@Autowired
public MemberUseCaseTest(
        MemberUseCase memberUseCase,
        MemberRepository memberRepository,
        PointRepository pointRepository,
        DatabaseCleanUp databaseCleanUp
) {
    this.memberUseCase = memberUseCase;
    this.memberRepository = memberRepository;
    this.pointRepository = pointRepository;
    this.databaseCleanUp = databaseCleanUp;
}
```

### Fixture 작성 규칙
- 도메인 객체의 **정적 팩토리 메서드를 우선 사용**
  (예: `Brand.create(...)`, `Member.register(...)`)
- 테스트 전용 fixture가 필요하면 메서드 이름 규칙을 aSaved* 로 prefix 사용
  (예: `MemberFixture.aSavedMember()`)
- **Reflection은 JPA가 강제로 채우는 필드(id, createdAt 등) 한정**
    - `ReflectionTestUtils.setField()` 사용
    - 그 외 필드는 정적 팩토리/빌더로 정상 경로 사용

### 테스트 메서드 구조
- given/when/then을 **`// given`, `// when`, `// then` 주석으로 구분한다**
    - 빈 줄만으로 나누면 given이 커질 때 경계가 보이지 않는다
    - 실행과 검증이 물리적으로 붙어 있으면(`mockMvc.perform().andExpect()` 같은 체인)
      `// when & then`으로 묶어 적는다. 억지로 나누지 않는다
- **한 블록 안에서 목적이 갈리면 한 줄 띄운다.** 특히 given이 길어질 때 효과가 크다
- 한 테스트는 한 동작(when)만 검증
- 단언이 **2개 이상이면 반드시 `assertAll`로 묶는다** (예외 없음)
- 단언이 1개일 때만 단독 `assertThat` 사용 가능
- **`@DisplayName`은 그 테스트가 보장하는 것을 전부 말한다.** 조건(when)뿐 아니라
    **then의 단언 전부**를 담는다. 메서드 이름은 `동작_조건` 형태로 짧게 두고, 상세는 `@DisplayName`이 진다

```java
@Test
@DisplayName("같은 Idempotency-Key로 다시 요청하면, 200응답이고 같은 주문번호를 반환하지만 isDuplicated는 참이고 재고는 한 번만 줄어든다")
void returnsSameOrder_whenIdempotencyKeyIsReused() { ... }
```

- 테스트가 깨지면 리포트에는 **이름만 뜬다.** 이름이 단언을 말하지 않으면 코드를 열어야 무엇이 깨졌는지 안다
- `assertAll`로 단언 여러 개를 묶는 만큼, 이름도 그것들을 말해야 짝이 맞는다
- **이름이 감당 못 할 만큼 길어지면 테스트를 쪼갤 신호다.** 길이가 곧 냄새 탐지기 역할을 한다

```java
@Test
void 브랜드를_조회하면_브랜드_정보를_반환한다() {
    // given
    Brand brand = BrandFixture.aValidBrand();
    brandRepository.save(brand);

    // when
    Brand result = brandService.getBrand(brand.getId());

    // then
    assertAll(
            () -> assertThat(result.getId()).isEqualTo(brand.getId()),
            () -> assertThat(result.getName()).isEqualTo(BrandFixture.DEFAULT_NAME),
            () -> assertThat(result.getDescription()).isEqualTo(BrandFixture.DEFAULT_DESCRIPTION)
    );
}
```
- **given의 모든 데이터는 변수로 추출한다.** when/then 안에 리터럴 값을
  직접 박지 않는다. 변수명이 곧 테스트 의도를 설명해야 한다.
- **변수명이 곧 테스트 의도를 설명해야 한다.** when/then 안에 의미 불명의
    리터럴을 직접 박지 않고, 의도가 드러나는 이름의 변수로 추출한다.
- **헬퍼 메서드 호출 결과도 변수로 받는다.** 픽스처든 테스트 안의 private 메서드든,
    호출을 when/then 안에 그대로 박지 않는다. 메서드 이름은 "무엇을 하는지"를 말하지만
    **변수 이름은 "그 값이 무엇인지"를 말한다.** 단언을 읽을 때 필요한 건 후자다.

```java
// ❌ 무엇과 무엇을 비교하는지 한눈에 안 들어온다
assertThat(stockOf(savedProduct)).isEqualTo(initialStock - orderQuantity);
assertThat(findOrder(idempotencyKey).getStatus()).isEqualTo(OrderStatus.ORDER_FAILED);

// ✅ 남은 재고와 실패한 주문을 비교한다는 게 드러난다
int remainingStock = stockOf(savedProduct);
Order failedOrder = findOrder(idempotencyKey);

assertAll(
        () -> assertThat(remainingStock).isEqualTo(initialStock - orderQuantity),
        () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.ORDER_FAILED)
);
```

> `assertAll` 안에서 특히 중요하다. 람다 안에 호출이 박혀 있으면 단언이 깨졌을 때
> **어떤 값이 문제였는지** 보려고 헬퍼를 따라가야 한다.

❌ **잘못된 예** — given이 when 안에 박혀있음:

```java
@Test
void 존재하지_않는_상품_조회_시_NOT_FOUND_예외가_발생한다() {
    assertThatThrownBy(() -> productUseCase.getProduct(
            new ProductUseCaseDto.GetProductInfo(Long.MAX_VALUE)))   // ← 의미 불명
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
}

@Test
void productId가_숫자가_아니면_400_Bad_Request를_반환한다() throws Exception {
    mockMvc.perform(get("/api/v1/products/string"))   // ← "string"이 뭘 뜻하는지 불명
            .andExpect(status().isBadRequest());
}
```

✅ **올바른 예** — given을 변수로 추출, 주석으로 블록 구분:

```java
@Test
void 존재하지_않는_상품_조회_시_NOT_FOUND_예외가_발생한다() {
    long nonExistentProductId = Long.MAX_VALUE;
    ProductUseCaseDto.GetProductInfo info = 
        new ProductUseCaseDto.GetProductInfo(nonExistentProductId);

    assertThatThrownBy(() -> productUseCase.getProduct(info))
            .isInstanceOfSatisfying(CoreException.class, e ->
                    assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
}

@Test
void productId가_숫자가_아니면_400_Bad_Request를_반환한다() throws Exception {
    String invalidProductId = "string";

    // given
    String invalidProductId = "string";

    // when & then
    mockMvc.perform(get("/api/v1/products/" + invalidProductId))
            .andExpect(status().isBadRequest());
}
```

#### given 안에서 목적이 갈릴 때

한 블록에 성격이 다른 준비가 섞이면 한 줄 띄워 나눈다.
아래는 앞이 **주문 데이터 준비**, 뒤가 **HTTP 요청 구성**이다.

```java
@Test
void 판매중인_상품을_주문하면_결제_대기_상태가_된다() {
    // given
    Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
    int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
    int orderQuantity = 2;
    Long expectedTotalAmount = ProductFixture.DEFAULT_PRICE.getAmount() * orderQuantity;

    HttpHeaders headers = headersOf(UUID.randomUUID().toString());
    OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
            List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), orderQuantity)));
    ParameterizedTypeReference<ApiResponse<OrderV1Dto.PlaceOrderResponse>> responseType =
            new ParameterizedTypeReference<>() {};

    // when
    ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> response = testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

    // then
    assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(expectedTotalAmount),
            () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow()
                    .getStockQuantity().getValue()).isEqualTo(initialStock - orderQuantity)
    );
}
```

### 테스트 안 private 헬퍼 이름 규칙

이름만 보고 **given에 쓸 것인지 then에 쓸 것인지** 알 수 있어야 한다.

| 종류 | 접두사 | 예 |
|---|---|---|
| 테스트 데이터를 **만든다** (given) | `a~` / `an~` — 픽스처와 같은 접두사 | `anOrderInfo(...)`, `aProductForBrand(...)` |
| 시스템 상태를 **읽어온다** (then) | `find~` | `findOrder(key)`, `findStockOf(product)` |

```java
// ❌ 읽어오는 헬퍼인데 이름이 그걸 말하지 않는다
private int stockOf(Product product) { ... }

// ✅
private int findStockOf(Product product) { ... }
```

- **when은 헬퍼로 감싸지 않는다.** 무엇을 실행하는지가 테스트 안에 그대로 보여야 한다.
    요청 조립·호출을 헬퍼로 빼면 단언이 무엇에 대한 것인지 따라가야 알게 된다
- 헬퍼 호출 결과는 [위 규칙](#테스트-메서드-구조)대로 **변수로 받는다.**
    `find~`가 "읽어온다"를 말하면, 변수 이름이 "읽어온 그 값이 무엇인지"를 말한다

### Repository 테스트 작성 기준

- ❌ **테스트 작성 금지**: Spring Data JPA가 메서드명으로 자동 생성하는 쿼리
  (예: `findById`, `findByBrandId`, `existsByEmail`, `deleteByName` 등)
- ✅ **테스트 작성 필수**: `@Query`, QueryDSL, 네이티브 쿼리, 복잡한 조건/조인,
  벌크 연산
- 판단 기준: "이 메서드의 쿼리 동작이 메서드명만 보고 명확한가?"
  → 명확하면 Spring Data JPA 신뢰, 모호하면 검증

## 참고 구현체 (필독)

새 도메인 작업 시 회원/포인트 모듈을 먼저 읽고 동일한 스타일을 따를 것.

### 회원 모듈
- `apps/commerce-api/src/main/java/com/loopers/domain/member/`
- `apps/commerce-api/src/main/java/com/loopers/application/member/`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/member/`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/member/`

### 포인트 모듈
- `apps/commerce-api/src/main/java/com/loopers/domain/point/`
- `apps/commerce-api/src/main/java/com/loopers/application/point/`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/point/`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/point/`

### 공통 예외
- `apps/commerce-api/src/main/java/com/loopers/support/error/CoreException.java`
- `apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java`

## 도메인 명세 문서

새 도메인 작업 시 해당 명세를 먼저 읽을 것:
- 회원: `docs/도메인모델/01_member.md`
- 포인트: `docs/도메인모델/02_point.md`
- 브랜드: `docs/도메인모델/03_brand.md`
- 상품: `docs/도메인모델/04_product.md`
- 상품 좋아요: `docs/도메인모델/05_prodocut-like.md`
- 공통 규칙: `docs/도메인모델/공통/`

## 작업 흐름

새 기능 추가 요청을 받으면:
1. 해당 도메인 명세 문서를 먼저 읽음
2. 회원/포인트 모듈에서 가장 유사한 케이스를 참고
3. TFD 절차는 `tfd-workflow` skill을 따름
4. 레이어별 상세 규칙은 각 레이어 skill을 따름
   (`domain-layer`, `application-layer`, `interface-layer`, `infrastructure-layer`)
