# Payment 애그리거트

> **범위**: 큐레이션 오픈마켓 MVP — 사용자 관점

---

## A. 행위

> **책임**: 확정된 주문의 금액을 실제로 받아내고, 그 결과를 사실로 확정한다. 포인트 결제는 요청 안에서, PG 결제는 콜백으로 끝난다 (참고: Payment-003). 재고를 되돌리는 판단은 Order가 갖고, Payment는 "결제가 성공했다 / 실패했다"를 확정해 넘긴다 (참고: 06_order.md Order-007, Payment-006).

### API 엔드포인트

| METHOD | URI | 설명 | 대응 UC |
|---|---|---|---|
| POST | `/api/v1/payments` | 결제 요청 | UC-1 |
| POST | `/api/v1/payments/pg/callback` | PG 결제 결과 수신 | UC-2 |

> 결제 단독 조회 엔드포인트는 두지 않는다. 결제 정보는 주문 상세 조회가 이미 담는다 (참고: Payment-007, 06_order.md UC-3).
> 결제는 주문이 `AWAITING_PAYMENT`가 된 뒤에 시작한다. 주문 요청이 결제까지 오케스트레이션하지 않는다 (참고: 06_order.md Order-003).

### UC-1. 결제 요청

**목적**
주문을 확정한 회원이 결제 수단을 골라 주문 금액을 지불한다.

**입력**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `memberId` | MemberId | 필수 | 결제하는 회원 (X-MEMBER-ID 헤더에서 추출) |
| `orderNumber` | String | 필수 | 결제 대상 주문 (참고: 06_order.md Order-011) |
| `method` | PaymentMethod | 필수 | `POINT` 또는 `CARD` |
| `cardType` | CardType | 조건부 | `method`가 `CARD`일 때 필수 |
| `cardNo` | String | 조건부 | `method`가 `CARD`일 때 필수 |

> **결제 금액은 요청에 포함하지 않는다** (참고: Payment-002).
> 주문의 내부 식별자 `id`도 받지 않는다. 주문을 가리키는 값은 `orderNumber` 하나다 (참고: 06_order.md Order-011).
> 결제 수단을 주문이 아니라 여기서 고르는 이유는, 사용자가 주문서를 확정한 뒤 결제 수단을 정하는 것이 실제 구매 흐름이기 때문이다 (참고: 06_order.md Order-003).

**출력**

| 필드 | 타입 | 설명 |
|---|---|---|
| `orderNumber` | String | 결제 대상 주문번호 |
| `method` | PaymentMethod | 사용한 결제 수단 |
| `status` | PaymentStatus | `POINT`는 `APPROVED`, `CARD`는 `PENDING` |
| `amount` | Long | 서버가 주문에서 가져온 결제 금액 (참고: Payment-002) |
| `approvedAt` | ZonedDateTime | 승인 시각. 승인 전에는 비어 있음 |
| `failureReason` | String | 실패 사유. 실패가 아니면 비어 있음 |

> **`CARD` 결제는 이 응답이 성공을 뜻하지 않는다.** `PENDING`으로 돌아가며 최종 결과는 콜백이 정한다 (참고: UC-2, Payment-003).
> 이 시점의 주문 상태는 아직 `AWAITING_PAYMENT`다. 클라이언트는 주문 상세 조회로 종결 여부를 확인한다 (참고: Payment-007).
> **PG 거래 식별자(`transactionKey`)는 응답에 담지 않는다.** 내부 대사 용도이며 사용자에게 의미가 없다 (참고: 06_order.md UC-3의 판단과 같다).

**전제 조건**
- 사용자는 로그인 상태여야 함
- 주문이 존재하고 요청 회원의 주문이어야 함 — 아니면 "찾을 수 없음"(404). 주문의 존재 여부 자체를 노출하지 않기 위해 403이 아니다 (참고: 06_order.md UC-3)
- 주문이 `AWAITING_PAYMENT`여야 함 — 아니면 `CONFLICT`(409)
- 해당 주문에 결제가 아직 없어야 함 — 있으면 `CONFLICT`(409) (참고: Payment-001)
- `method`가 `POINT`면 포인트 잔액이 주문 금액 이상이어야 함

**처리 정책**

**POINT — 세 개의 트랜잭션으로 나뉜다**

**T0. 접수** — 커밋된다

1. `orderNumber`로 주문을 조회하고 `Order.isOwnedBy(memberId)`와 `Order.isPayable()`을 검사한다
2. 결제 금액을 `Order.totalAmount`에서 가져온다. 요청이 전달한 값을 쓰지 않는다 (참고: Payment-002)
3. `Payment.request(orderId, memberId, method, amount)`로 `PENDING` 결제를 저장하고 **커밋한다**

> **접수가 별도 트랜잭션인 이유는 두 가지다.** 승인(T1)이 롤백되면 그 안에서 만든 결제 행도 함께 사라져 T2가 실패를 기록할 대상이 없어진다. 그리고 **커밋된 행이 없으면 `orderId` UNIQUE가 동시 결제 요청을 막지 못한다** (참고: Payment-001).
> 이는 주문이 접수(T1)를 먼저 커밋해 멱등성 판정 근거를 남기는 것과 같은 구조다 (참고: 06_order.md Order-004).
> 같은 주문에 두 번째 결제 요청이 오면 UNIQUE 위반이 나며, 그 위반이 곧 "이미 결제가 붙은 주문"이라는 신호다. 제약 위반은 커밋 시점에 터지므로 **트랜잭션 밖에서** `CONFLICT`로 바꾼다.

**T1. 승인** — 커밋된다

4. `Point.use(amount)`로 잔액을 차감한다. 부족하면 `CONFLICT`를 던진다 (참고: 02_point.md)
5. `Payment.approve(null)`로 결제를 `APPROVED`로 전이한다
6. `Order.pay()`로 주문을 `PAID`로 전이하고 `paidAt`을 기록한다

**T2. 실패 처리** — T1이 실패했을 때만 수행하며, 별도로 커밋된다

7. `Payment.fail(reason)`로 결제를 `FAILED`로 전이한다
8. 각 `OrderLine.quantity`만큼 `Product.increaseStock()`으로 재고를 복원한다 (참고: 06_order.md Order-007, Payment-006)
9. `Order.markPaymentFailed()`로 주문을 `PAYMENT_FAILED`로 전이한다
10. T1에서 발생한 예외를 그대로 클라이언트에 전파한다 (4xx)

> **포인트는 되돌릴 필요가 없고 재고는 되돌려야 한다.** 포인트 차감은 T1 안에 있어 롤백으로 함께 되돌아가지만, 재고는 다른 트랜잭션(주문 확정)에서 빠진 것이라 T2가 명시적으로 복원한다 — 이것이 `ORDER_FAILED`와 `PAYMENT_FAILED`의 차이다 (참고: 06_order.md Order-005, Order-007).
> **잔액 부족도 결제 실패다.** 주문이 `PAYMENT_FAILED`로 종결되므로, 포인트를 충전해 다시 결제하는 경로가 지금은 없다 (참고: C.2).

> **T2는 `CoreException`에만 건다.** 잔액 부족이나 상태 전이 실패처럼 커밋 전에 결정되는 도메인 실패만이 "T1이 커밋되지 않았다"를 보장한다. 락 타임아웃이나 커밋 실패는 포인트가 빠졌는지조차 알 수 없어, 그 상태에서 재고를 되돌리면 근거 없는 보상이 된다. 그런 실패는 결제를 `PENDING`으로 남긴 채 전파한다 (참고: C.2).

**CARD — 콜백이 종결시킨다**

> 아래는 명세이며 이번 구현 범위 밖이다. UC-2와 함께 만든다.

**T1. 승인 요청** — 커밋된다

4. `PENDING` 결제를 저장하고 **커밋한다.** PG를 부르기 전에 커밋한다
5. PG에 승인을 요청한다. 이 응답은 "접수했다"까지이며 승인 결과가 아니다
6. `PENDING` 상태를 그대로 응답한다. 주문은 `AWAITING_PAYMENT`에 머문다

> **PG를 부르기 전에 커밋하는 이유는 콜백이 먼저 도착할 수 있기 때문이다.** 결제 행이 아직 커밋되지 않았는데 콜백이 오면, 콜백은 볼 것이 없어 404로 되돌아간다 (참고: Payment-005).
> PG 요청 자체가 거절되면(연결 불가, 4xx) POINT의 T2와 같은 실패 처리를 수행한다. **타임아웃은 다르다** — 요청이 도달했는지 알 수 없으므로 실패로 확정하지 않는다 (참고: C.2).

**그 외**
- 같은 주문에 결제를 두 번 요청하면 두 번째는 409다. `Payment.orderId`가 UNIQUE라 DB가 막는다 (참고: Payment-001)
- **결제 요청은 상품 상태와 재고를 다시 보지 않는다.** 재고는 주문 확정에서 이미 확보했다 (참고: 06_order.md Order-005, Order-009)
- 결제 금액은 주문 시점에 스냅샷된 단가로 계산된 값이다. 주문과 결제 사이에 상품 가격이 바뀌어도 결제 금액은 그대로다 (참고: 06_order.md Order-001)

**도메인 협력**
- `Order.isOwnedBy(memberId)` — 소유권 검사
- `Order.isPayable()` — 결제 가능 상태 검사
- `Payment.request(orderId, memberId, method, amount)` — 결제 접수
- `Point.use(amount)` — 포인트 차감 (`POINT`만)
- `Payment.approve(transactionKey)` / `Payment.fail(reason)` — 결제 종결
- `Order.pay()` / `Order.markPaymentFailed()` — 주문 종결
- `Product.increaseStock(quantity)` — 실패 시 재고 복원

---

### UC-2. PG 결제 결과 수신

**목적**
PG가 카드 승인 결과를 알려오면 결제와 주문을 종결한다.

**입력**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `transactionKey` | String | 필수 | PG 거래 식별자 |
| `orderNumber` | String | 필수 | 대상 주문 (참고: 06_order.md Order-011) |
| `status` | String | 필수 | PG가 알려주는 승인 결과 |
| `amount` | Long | 필수 | PG가 승인한 금액. **대조용이며 결제 금액의 근거가 아니다** (참고: Payment-002) |
| `reason` | String | 선택 | 실패 사유 |

> **이 엔드포인트는 사용자가 부르지 않는다.** 호출자는 PG이므로 `X-MEMBER-ID`가 없고 소유권 검사도 하지 않는다. 대상은 `orderNumber`로만 특정한다.

**출력**

수신 확인만 반환한다. 본문은 두지 않는다.

> **처리에 실패하면 5xx로 응답해 PG의 재전송을 유도한다.** 콜백 처리가 멱등하므로 재전송이 안전하다 (참고: Payment-005). 실패를 삼키고 200을 주면 결제가 `PENDING`에 영구히 남는다.

**전제 조건**
- `orderNumber`에 해당하는 `CARD` 결제가 존재해야 함 — 없으면 "찾을 수 없음"(404)

**처리 정책**

1. `orderNumber`로 결제와 주문을 조회한다
2. `Payment.isFinalized()`면 **아무것도 하지 않고 끝낸다.** 이미 종결된 결제에 두 번째 결과를 적용하지 않는다 (참고: Payment-005)
3. **승인 금액을 `Order.totalAmount`와 대조한다. 다르면 PG가 성공을 알려왔더라도 실패로 처리한다** (참고: Payment-002, 06_order.md Order-002)
4. 성공이면 — 한 트랜잭션에서 `Payment.approve(transactionKey)` 후 `Order.pay()`
5. 실패이거나 금액이 어긋나면 — 한 트랜잭션에서 `Payment.fail(reason)`, 각 `OrderLine.quantity`만큼 재고 복원, `Order.markPaymentFailed()` (참고: 06_order.md Order-007, Payment-006)

**그 외**
- 같은 콜백이 여러 번 와도 결과는 같다. 2번에서 걸러지므로 **재고가 두 번 복원되지 않는다** (참고: Payment-005)
- **콜백이 아예 오지 않으면 결제는 `PENDING`, 주문은 `AWAITING_PAYMENT`로 남는다.** 정리하는 주체가 없다 (참고: C.2, 06_order.md Order-007)
- 보상(재고 복원)이 실패하면 재고가 묶인 채 복구 수단이 없다. 보상의 보상은 없다 (참고: 06_order.md Order-007, C.2)

**도메인 협력**
- `Payment.isFinalized()` — 멱등 판정
- `Payment.approve(transactionKey)` / `Payment.fail(reason)` — 결제 종결
- `Order.pay()` / `Order.markPaymentFailed()` — 주문 종결
- `Product.increaseStock(quantity)` — 재고 복원

---

### 도메인 이벤트

> 현재 단계에서는 이벤트 도입 보류.

향후 후보:
- `PaymentApproved` — 결제가 승인됨
- `PaymentFailed` — 결제가 실패함

> 06_order.md의 `OrderPaid`와 겹친다. 이벤트를 도입할 때 어느 쪽이 사실의 원본인지 함께 정한다 (참고: Payment-004).

---

## B. 구조

> 위 행위를 가능하게 하는 내부 구성. 애그리거트 루트와 값 객체, 외부 관계로 이루어진다.

### B.1. Payment : Entity, Aggregate Root

#### 속성

- `id` : Long — 내부 식별자
- `orderId` : Long — 결제 대상 주문 (ID 참조, UNIQUE — 참고: Payment-001)
- `memberId` : Long — 결제한 회원 (ID 참조)
- `method` : PaymentMethod — 결제 수단
- `status` : PaymentStatus — 결제 상태
- `amount` : Money — 결제 금액. 주문 총액을 복사한 값 (참고: Payment-002)
- `transactionKey` : String — PG 거래 식별자. `CARD`가 승인된 뒤에만 존재
- `approvedAt` : ZonedDateTime — 승인 시각. `APPROVED`가 아니면 없음
- `failureReason` : String — 실패 사유. `FAILED`가 아니면 없음

**불변식**

상태와 결제 수단에 따라 성립하는 조건이 다르다.

| 조건 | 적용 대상 |
|---|---|
| `orderId`는 시스템 내에서 유일 | 전부 |
| `amount`는 대상 주문의 `totalAmount`와 같다 | 전부 |
| `amount`는 양수 (`Money` 타입이 음수를 막는다) | 전부 |
| `approvedAt`이 존재한다 | `APPROVED` |
| `failureReason`이 존재한다 | `FAILED` |
| `transactionKey`가 존재한다 | `method`가 `CARD`이고 `APPROVED` |
| `transactionKey`가 없다 | `method`가 `POINT` |

#### 행위

- `request(orderId, memberId, method, amount)` : Payment — 결제를 접수한다 (정적 팩토리). 상태는 `PENDING`
- `approve(transactionKey)` : void — `APPROVED`로 전이하고 `approvedAt`을 기록한다. `PENDING`이 아니면 예외. `POINT`는 `transactionKey`가 `null`이다
- `fail(reason)` : void — `FAILED`로 전이하고 사유를 남긴다. `PENDING`이 아니면 예외
- `isFinalized()` : Boolean — 종결된 결제인지 반환한다. `status.isFinalized()`에 위임한다 (UC-2의 멱등 판정)
- `isOwnedBy(memberId)` : Boolean — 해당 회원의 결제인지 반환한다

> 결제 취소(`cancel()`)와 환불(`refund()`)은 이번 feature 범위 밖이다. 주문 취소 정책이 정해질 때 함께 추가한다 (참고: C.2, 06_order.md C.2).

---

### B.2. PaymentMethod : Value Object

결제 수단을 나타내는 열거형.

#### 속성

- `POINT` — 보유 포인트로 결제. 요청 트랜잭션 안에서 확정된다
- `CARD` — PG를 통한 카드 결제. 콜백으로 확정된다

#### 행위

- `isSettledInRequest()` : Boolean — 요청 안에서 결과가 확정되는 수단인지 반환한다. `POINT`만 `true`

> 이 열거형이 나누는 것은 "무엇으로 내느냐"가 아니라 **"결과가 언제 확정되느냐"**다. 두 수단의 처리 흐름이 갈리는 지점이 전부 이 판정에 걸린다 (참고: Payment-003).

---

### B.3. PaymentStatus : Value Object

결제 상태를 나타내는 열거형.

#### 속성

- `PENDING` — 결제가 접수되어 결과를 기다리는 상태
- `APPROVED` — 승인 완료
- `FAILED` — 승인 실패. **주문의 재고가 복원된 상태** (참고: 06_order.md Order-007)

**전이 규칙**

| 출발 | 갈 수 있는 곳 |
|---|---|
| `PENDING` | `APPROVED`, `FAILED` |
| `APPROVED` | 없음 |
| `FAILED` | 없음 |

#### 행위

- `isFinalized()` : Boolean — 종결된 상태인지 반환한다. `APPROVED`, `FAILED`가 `true`
- `requireTransitionTo(target)` : void — 전이할 수 없으면 `CONFLICT` 예외를 던진다

> **`OrderStatus`의 `allowedNextStatuses` + `requireTransitionTo()` 방식을 그대로 따른다** (`domain/order/OrderStatus.java`). 규칙과 그 집행이 함께 있어야 어긋나지 않는다는 판단이 같다. 두 상태 기계가 서로 다른 방식으로 전이를 검사하면 어느 쪽이 규칙인지 알 수 없게 된다.
> `CANCELED`는 두지 않는다. 결제 취소가 이번 범위 밖이라 진입 경로가 없기 때문이다 — `OrderStatus`에서 `CANCELED`를 뺀 것과 같은 이유다 (참고: C.2, 06_order.md B.3).

---

### B.4. 다른 애그리거트와의 관계

- **Order** ← `orderId`로 참조 (단방향, ID 참조). 1:1
    - **Payment → Order 방향만 존재한다.** Order는 Payment를 모른다 (참고: Payment-001, Payment-004, 06_order.md B.4)
    - `Order.pay()`와 `Order.markPaymentFailed()`를 부르는 곳은 결제 흐름뿐이다
    - `orderId`에 UNIQUE 제약이 있어 한 주문에 결제는 하나다 (참고: Payment-001)
- **Point** — `Point.use(amount)` 호출 (참고: 02_point.md)
    - `POINT` 결제에서만 쓴다. `CARD` 결제는 Point를 건드리지 않는다
- **Product** — 결제 실패 시 `increaseStock()`으로 재고를 복원한다 (참고: 06_order.md Order-007, Payment-006)
    - 복원 수량은 항상 `OrderLine.quantity`이며 다른 근거를 두지 않는다
- **Member** ← `memberId`로 참조 (단방향, ID 참조)
    - 회원 도메인은 별도 컨텍스트로, ID로만 참조
- **PG** — 외부 시스템. 애그리거트가 아니다
    - 승인 요청은 내보내고 결과는 콜백으로 받는다. 우리 트랜잭션 경계 밖이다 (참고: Payment-003)

---

## C. 설계 결정

> 확정된 결정과 보류된 결정을 함께 모은다. 보류된 항목이 결정되면 C.2 → C.1로 이동한다.

### C.1. 확정된 결정

> **왜 이런 선택을 했는지** 보존한다. 결정은 누적될 뿐 수정되지 않는다.

#### Payment-001. Payment를 별도 애그리거트 루트로 두고 orderId로 주문을 참조한다
- **일시**: 2026-08-27
- **결정**: `Payment`는 `Order` 애그리거트 내부가 아니라 자기 루트다. `orderId`로 주문을 단방향 참조하며 이 컬럼에 UNIQUE 제약을 둔다. Order는 Payment를 알지 않는다.
- **이유**:
    - 결제는 주문과 **생명주기가 다르다.** 주문이 확정된 뒤 별도 요청으로 시작되고(참고: 06_order.md Order-003), PG 결제는 콜백으로 나중에 끝난다. 주문 애그리거트 안에 넣으면 이 비동기 구간을 주문 트랜잭션이 떠안게 된다
    - `OrderLine`을 애그리거트 내부로 둔 판단과 대비된다. 라인은 주문 없이 존재할 수 없지만, **결제는 자기 상태 기계와 자기 트랜잭션 경계를 갖는다** (참고: 06_order.md Order-008)
    - UNIQUE를 둔 것은 한 주문에 결제가 두 번 붙는 것을 DB가 막게 하기 위해서다. 응용 계층의 중복 검사만으로는 동시 요청을 막을 수 없다
- **영향**:
    - 주문 조회에서 결제 정보를 보려면 조인이 필요하다. 1:1이라 한 번으로 끝난다 (참고: 06_order.md UC-2)
    - 결제 요청 전인 주문에는 Payment 행이 없다. 결제 관련 필드가 전부 비어 있는 것이 정상이다
    - **`PAYMENT_FAILED` 주문의 재결제가 이 UNIQUE에 막힌다.** 재결제를 열려면 제약부터 다시 봐야 한다 (참고: C.2, 06_order.md C.2)
    - 주문과 결제가 서로 다른 트랜잭션에서 저장되므로 둘의 상태가 잠깐 어긋나는 구간이 생긴다
- **관련 결정**: Payment-004
- **관련 애그리거트**: Order (참고: 06_order.md B.4)
- **재검토 시점**: 재결제나 분할 결제가 필요해질 때 → UNIQUE를 풀고 "유효한 결제는 하나"라는 조건으로 바꾸는 방식 검토.

#### Payment-002. 결제 금액은 요청에서 받지 않고 주문에서 가져온다
- **일시**: 2026-08-27
- **결정**: 결제 요청 바디에 금액 필드를 두지 않는다. 결제 금액은 `Order.totalAmount`를 복사해 쓴다. PG 콜백이 전달하는 승인 금액은 대조에만 쓰며, 값이 다르면 승인하지 않는다.
- **이유**:
    - 06_order.md Order-002가 정한 원칙을 결제 쪽에서 그대로 잇는다. **주문에서 금액을 안 받아놓고 결제에서 받으면 조작 지점을 옮긴 것뿐이다**
    - 결제가 별도 요청이라 조작 지점이 하나 더 있다 (참고: 06_order.md Order-003). 그 지점을 닫는다
    - PG 승인 금액을 대조하는 이유는 우리가 보낸 금액과 PG가 승인한 금액이 다를 수 있기 때문이다. 요청이 중간에 변조되었거나 PG 쪽 오류인 경우이며, 어느 쪽이든 승인해서는 안 된다
- **영향**:
    - `Payment.amount`는 항상 주문 총액과 같다. B.1의 불변식이 된다
    - 부분 결제와 복합 결제(포인트 + 카드)가 이 구조에서는 불가능하다 (참고: C.2)
    - 금액 불일치는 실패로 처리되어 주문이 `PAYMENT_FAILED`가 되고 재고가 복원된다 (참고: UC-2)
- **관련 결정**: Payment-006
- **관련 애그리거트**: Order (참고: 06_order.md Order-002)
- **재검토 시점**: 쿠폰·할인이 도입되어 주문 총액과 결제 금액이 갈릴 때.

#### Payment-003. 포인트 결제는 요청 안에서, PG 결제는 콜백으로 확정한다
- **일시**: 2026-08-27
- **결정**: `POINT`는 결제 요청 트랜잭션 안에서 차감·승인·주문 종결까지 끝낸다. `CARD`는 `PENDING` 결제만 남기고 응답하며, 최종 결과는 PG 콜백이 정한다. 두 수단이 하나의 `Payment`와 하나의 상태 기계를 공유한다.
- **이유**:
    - 포인트는 우리 DB 안의 잔액이라 외부 왕복이 없다. 굳이 비동기로 만들면 사용자는 즉시 알 수 있는 결과를 기다리게 된다
    - PG는 외부 승인 왕복이 있어 트랜잭션 안에 넣을 수 없다. 주문과 결제를 분리한 이유와 같다 (참고: 06_order.md Order-003)
    - 그럼에도 **두 수단이 같은 애그리거트를 쓰는 이유는 종결 이후의 처리가 완전히 같기 때문이다.** 승인되면 주문이 `PAID`, 실패하면 재고 복원 후 `PAYMENT_FAILED`다. 수단별로 다른 타입을 만들면 이 공통 처리가 두 벌이 된다
- **영향**:
    - `POINT` 결제는 `PENDING` 상태로 관측되지 않는다. 한 트랜잭션 안에서만 존재한다
    - `CARD` 결제 응답의 `status`는 성공을 뜻하지 않는다. 클라이언트가 이를 오해하면 결제되지 않은 주문을 완료로 표시한다 (참고: UC-1 출력)
    - `transactionKey`는 `CARD`에만 있다. B.1의 불변식이 수단에 따라 갈린다
    - 두 경로가 같은 실패 처리를 공유하므로 보상 로직은 한 곳에 둔다 (참고: Payment-006, 06_order.md Order-007)
- **관련 결정**: Payment-005(콜백 멱등)
- **재검토 시점**: 결제 수단이 셋 이상으로 늘어 상태 기계가 갈릴 때.

#### Payment-004. 주문 상태를 종결시키는 것은 Payment다
- **일시**: 2026-08-27
- **결정**: `AWAITING_PAYMENT`에서 나가는 전이(`PAID`, `PAYMENT_FAILED`)는 결제 흐름만 트리거한다. Order는 Payment를 참조하지 않으며, 주문 자신이 결제 결과를 조회해 상태를 바꾸는 경로를 두지 않는다.
- **이유**:
    - **결제 결과를 아는 곳은 결제뿐이다.** 사실을 아는 쪽이 그 사실의 결과를 적용해야 중간 전달자가 생기지 않는다
    - 양방향 참조를 열면 "누가 먼저 바뀌는가"가 모호해진다. 단방향이면 순서가 코드에 드러난다
    - 주문 조회가 결제를 읽어 상태를 유추하는 방식은, 조회할 때마다 판정이 달라질 수 있고 그 판정이 저장되지 않는다
- **영향**:
    - `OrderStatus`의 `AWAITING_PAYMENT` 전이는 결제 구현 시점에 열린다. **지금은 닫혀 있다** (참고: C.3)
    - `Order.pay()`와 `Order.markPaymentFailed()`의 호출자는 결제 흐름 하나뿐이다. 다른 곳에서 부르면 이 결정이 깨진다
    - 결제 없이 주문만 `PAID`로 만들 수 있는 경로가 없다. 테스트 픽스처도 결제를 거치거나 상태를 직접 세팅해야 한다
- **관련 결정**: Payment-001(단방향 참조)
- **관련 애그리거트**: Order (참고: 06_order.md B.4)
- **재검토 시점**: 관리자 수동 결제 처리나 정산 보정이 필요해질 때.

#### Payment-005. PG 콜백은 멱등하게 처리한다
- **일시**: 2026-08-27
- **결정**: 콜백을 받으면 먼저 `Payment.isFinalized()`를 확인하고, 이미 종결된 결제면 아무것도 하지 않고 정상 응답한다. 처리에 실패하면 5xx로 응답해 PG의 재전송을 유도한다.
- **이유**:
    - **재전송은 PG의 기본 동작이다.** 우리가 응답을 늦게 주거나 네트워크가 끊기면 같은 결과가 다시 온다. 방어하지 않으면 재고가 두 번 복원되거나 주문이 이미 종결된 상태에서 전이를 시도해 예외가 난다
    - 종결 여부를 판정 근거로 삼는 이유는 그것이 이미 커밋된 사실이기 때문이다. 별도 수신 이력 테이블을 두면 관리 대상이 하나 늘고, 결국 같은 판정을 한다
    - 실패를 삼키고 200을 주면 재전송이 오지 않아 **결제가 `PENDING`에 영구히 남는다.** 되살릴 수단이 없다
    - PG를 부르기 전에 `PENDING` 결제를 커밋하는 것도 같은 이유다. 콜백이 먼저 도착해도 볼 것이 있어야 한다 (참고: UC-1)
- **영향**:
    - 같은 콜백이 여러 번 와도 재고는 한 번만 복원된다 (참고: 06_order.md Order-007)
    - 종결된 결제에 **다른 결과**를 전달하는 콜백도 무시된다. PG가 승인 후 취소를 통보하는 경우가 여기 걸린다 (참고: C.2)
    - 5xx 응답은 PG 대시보드에 실패로 잡힌다. 재전송이 반복되면 모니터링에 노출된다 — 의도한 동작이다
- **관련 결정**: Payment-003
- **재검토 시점**: PG가 승인 취소나 부분 취소를 콜백으로 통보하기 시작할 때.

#### Payment-006. 재고 복원은 Order 쪽 로직을 Payment가 호출한다
- **일시**: 2026-08-27
- **결정**: 결제 실패 시의 재고 복원 로직은 주문 도메인에 두고, 결제 흐름이 그것을 호출한다. Payment는 복원 수량을 직접 계산하지 않는다.
- **이유**:
    - **차감한 곳이 되돌려야 한다.** 재고를 뺀 결정은 06_order.md Order-005이고 복원 수량의 근거는 `OrderLine.quantity`다. 두 로직이 떨어져 있으면 어긋난다
    - Payment는 `OrderLine`을 알지 못한다. 라인은 Order 애그리거트 내부이며 Order를 통해서만 접근한다 (참고: 06_order.md Order-008)
    - `POINT` 실패 경로와 `CARD` 콜백 실패 경로가 같은 복원을 수행한다. 한 곳에 두어야 두 벌이 되지 않는다 (참고: Payment-003)
- **영향**:
    - Payment 쪽 코드에는 "실패했다"는 사실과 그 전달만 남는다. 재고 수량이 등장하지 않는다
    - 복원과 `Order.markPaymentFailed()`가 같은 트랜잭션에 있어야 한다. 재고만 돌고 주문이 `AWAITING_PAYMENT`로 남으면 이중 결제가 가능해진다
    - **보상이 실패하면 재고가 묶인 채 복구 수단이 없다** (참고: 06_order.md Order-007, C.2)
- **관련 결정**: Payment-002, Payment-004
- **관련 애그리거트**: Order, Product (참고: 06_order.md Order-007)
- **재검토 시점**: 재고 정합성 보정 배치를 도입할 때.

#### Payment-007. 결제 단독 조회 엔드포인트를 두지 않는다
- **일시**: 2026-08-27
- **결정**: `GET /api/v1/payments/...`를 만들지 않는다. 사용자가 결제 결과를 확인하는 경로는 주문 상세 조회(`GET /api/v1/orders/{orderNumber}`) 하나다.
- **이유**:
    - 06_order.md UC-3이 이미 결제 수단·결제 상태·승인 시각·실패 사유를 담는다. 같은 정보를 두 엔드포인트가 주면 응답이 갈릴 때 어느 쪽이 맞는지 알 수 없다
    - **사용자가 알고 싶은 것은 "결제가 됐나"가 아니라 "주문이 됐나"다.** 결제는 그 수단이다
    - `CARD` 결제는 콜백을 기다리므로 클라이언트가 폴링해야 하는데, 폴링 대상도 주문이면 화면이 읽는 리소스가 하나로 유지된다
- **영향**:
    - 결제 상태만 필요한 화면도 주문 상세를 부른다. 응답이 라인 목록까지 포함해 조금 크다
    - 결제 식별자를 외부에 노출할 일이 없다. `Payment.id`는 내부에만 남는다 (참고: 06_order.md Order-011의 판단과 같은 성격)
    - 관리자용 결제 조회가 필요해지면 별도 API로 만든다. 사용자 API에 섞지 않는다
- **관련 결정**: Payment-001
- **재검토 시점**: 결제 이력을 주문과 무관하게 훑어야 하는 요구가 생길 때.

### C.2. 보류된 결정

> 아직 결정되지 않았거나 **앞으로 다룰 항목**을 모은다. 결정되면 C.1로 이동한다.

- [ ] **환불과 PG 승인 취소** — 주문 취소가 열리면 함께 필요하다. `PaymentStatus`에 취소 상태 추가, 포인트 복원, PG 승인 취소 호출이 여기 딸린다 (관련: 06_order.md C.2 "주문 취소와 환불")
- [ ] **`PAYMENT_FAILED` 주문의 재결제** — `Payment.orderId` UNIQUE에 막힌다. 재고 재확보도 선행되어야 한다. 잔액 부족으로 실패한 사용자가 충전 후 다시 결제할 경로가 지금은 없다 (관련: Payment-001, 06_order.md C.2)
- [ ] **`PENDING`으로 남은 결제의 정리** — 락 타임아웃이나 커밋 실패처럼 의미를 모르는 실패로 T1이 끝나면 보상이 돌지 않아 결제가 `PENDING`에 체류한다. `orderId` UNIQUE가 그 주문의 재결제를 영구히 막는다. 06_order.md C.2의 "`PENDING`으로 남은 주문의 정리"와 같은 성격이다 (관련: UC-1, Payment-001)
- [ ] **콜백이 오지 않는 경우** — 결제가 `PENDING`, 주문이 `AWAITING_PAYMENT`로 영구 체류한다. 재고도 묶인다. PG 상태 조회 폴링이나 대사 배치가 필요하다 (관련: Payment-005, 06_order.md Order-007)
- [ ] **PG 요청 타임아웃과 재시도** — 요청이 도달했는지 알 수 없어 실패로 확정할 수 없다. 확정하면 승인된 결제를 실패로 만들 수 있다 (관련: UC-1)
- [ ] **포인트 + 카드 복합 결제, 부분 결제** — `Payment.amount`가 주문 총액과 같다는 불변식과 `orderId` UNIQUE를 함께 풀어야 한다 (관련: Payment-001, Payment-002)
- [ ] **`transactionKey` 기준 정산 대사** — PG 거래 내역과 우리 결제 내역을 맞춰보는 배치 (관련: Payment-007)
- [ ] **보상(T2)이 실패했을 때의 복구 수단** — 원래 예외가 덮이던 것은 해결했다. `markFailedPreservingCause`가 보상 실패를 `addSuppressed`로 매달아 원래 원인과 함께 올린다. **남은 것은 보상이 돌지 못하는 경우 자체다.**
    - 상품이 내려가 복원이 막히던 경로는 없앴다 — `retrieveForRestore`는 노출 여부를 보지 않는다 (참고: Order-007)
    - **비관적 락 타임아웃** — 복원도 `PESSIMISTIC_WRITE`로 읽는다. 같은 상품을 다른 주문이 잡고 있으면 `innodb_lock_wait_timeout`(기본 50초)에 걸려 T2가 통째로 롤백된다. 인기 상품일수록 확률이 오른다
    - **T2 커밋 실패** — 커넥션 유실이나 데드락으로 커밋이 깨지는 경우
    - 두 경우 모두 **결제는 `PENDING`, 재고는 빠진 채, 주문은 `AWAITING_PAYMENT`로 남는다.** 예외는 suppressed로 남으니 진단은 되지만 되돌려주는 수단은 없다. 재시도나 보정 배치가 필요하다
    - (관련: Payment-006, UC-1의 "`PENDING`으로 남은 결제의 정리", 06_order.md C.2)
- [ ] 결제 수단별 한도와 검증 규칙 (카드 번호 형식 등)
- [ ] 도메인 이벤트 도입 (`PaymentApproved`, `PaymentFailed`)

### C.3. 구현 현황

> UC-1의 POINT 경로는 구현되어 있다. CARD와 UC-2(콜백)는 명세만 있고 코드는 없다.

| 대상 | 상태 |
|---|---|
| `domain/order/OrderStatus.java` | `AWAITING_PAYMENT → {PAID, PAYMENT_FAILED}` 전이 개방 완료 |
| `domain/order/Order.java` | `paidAt`, `pay()`, `markPaymentFailed()` 추가 완료 |
| `domain/product/Product.java` | `increaseStock(quantity)` 추가 완료 |
| `domain/payment` | `Payment`, `PaymentStatus`, `PaymentService`, `PaymentRepository` 완료. **`PaymentMethod`는 `POINT`만 있다** — CARD는 UC-2와 함께 추가한다 |
| `application/payment` | `PaymentUseCase` + `PaymentProcessor`(T0/T1/T2) 완료 |
| `interfaces/api/payment` | 결제 요청 엔드포인트 완료. 콜백 엔드포인트는 없다 |
| PG 클라이언트 | 없음. UC-2와 함께 만든다 |

> `OrderStatusTest.throwsConflict_whenSourceIsNotPending`은 전이를 열어도 **빨간불을 내지 않았다.** 목표 상태를 `AWAITING_PAYMENT`와 `ORDER_FAILED` 둘만 검사해 새로 열린 `PAID`·`PAYMENT_FAILED`와 겹치지 않았기 때문이다. 거짓이 된 것은 DisplayName의 "어디로도 전이할 수 없고"였다.
> 지금은 출발을 실제 종결 상태로 좁히고 **목표는 `values()` 전부를 훑도록** 고쳤다. 같은 실수가 다시 조용히 지나가지 않는다.
