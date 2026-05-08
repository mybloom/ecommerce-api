# Product 애그리거트

> **범위**: 큐레이션 오픈마켓 MVP — 사용자 관점

---

## A. 행위

> **책임**: 판매되는 상품의 정보를 표현하고, 판매 상태와 재고를 관리한다. 사용자가 탐색하고 구매하는 단위로서, 브랜드에 소속되며 좋아요의 대상이 된다.

### UC-1. 상품 목록 조회

**목적**  
사용자가 판매 중인 상품들을 둘러보며 관심 상품을 발견한다.

**입력**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| brandId | BrandId | 선택 | 없음 | 단일 브랜드 필터. 없으면 전체 |
| sort | ProductSort | 선택 | `latest` | 정렬 기준 |
| page | Int | 선택 | 1 | 페이지 번호 |
| size | Int | 선택 | 20 | 페이지 크기 |

**정렬 기준 (`ProductSort`)**
- `latest` — 등록일 내림차순
- `priceDesc` — 가격 높은순
- `likeDesc` — 좋아요 많은순

**출력**

상품 카드 목록과 페이징 메타.

상품 카드 정보:
- 상품 ID, 상품명, 대표 이미지
- 브랜드 ID, 브랜드명
- 가격, 좋아요 수, 품절 여부
- (로그인 사용자 한정) 내가 좋아요 눌렀는지

페이징 메타:
- 전체 개수, 현재 페이지, 전체 페이지 수, 다음 페이지 존재 여부

**필터 정책**
- `status == ON_SALE`인 상품만 노출 (`OFF_SALE`, `HIDDEN`은 제외)
- 품절 상품(`stockQuantity == 0`)은 노출됨
- 소속 브랜드가 `ACTIVE`가 아닌 상품은 제외 (참고: brand.md Brand-001) 

**정렬 정책**
- 사용자가 선택한 정렬 기준을 그대로 적용
- 품절 상품도 동일 정렬에 포함됨 (참고: Product-002)
- 정렬 결과 안정성을 위해 2차 정렬 키로 `productId` 사용
    - 예: `likeDesc` → `ORDER BY likeCount DESC, productId DESC`

### 도메인 이벤트

> 현재 단계에서는 이벤트 도입 보류. 향후 필요 시점에 추가.

향후 후보:
- `ProductRegistered` — 상품 등록됨
- `ProductSoldOut` — 상품 품절됨
- `ProductRestocked` — 상품 재입고됨

---

## B. 구조

> 위 행위를 가능하게 하는 내부 구성. 애그리거트 루트와 값 객체, 외부 관계로 이루어진다.

### B.1. Product : Entity, Aggregate Root

#### 속성

- `id` : Long — 식별자
- `name` : String — 상품명
- `description` : String — 상세 설명
- `representativeImage` : ImageUrl — 대표 이미지
- `brandId` : Long — 소속 브랜드 (ID 참조)
- `price` : Money — 판매 가격
- `stockQuantity` : StockQuantity — 재고 수량
- `likeCount` : Int — 좋아요 수 (캐시)
- `status` : ProductStatus — 판매 상태
- `openedAt` : ZonedDateTime — 상품 개시 일시

**불변식**
- `price`는 0 이상
- `likeCount`는 0 이상

**파생 속성**
- `isSoldOut` : Boolean — `stockQuantity == 0`

#### 행위

- `increaseLikeCount()` : 좋아요 수를 1 증가시킨다 (ProductLike 추가 시 호출, 참고: Product-003)
- `decreaseLikeCount()` : 좋아요 수를 1 감소시킨다 (ProductLike 삭제 시 호출, 참고: Product-003)

> 그 외 행위(상품 등록, 가격 변경, 판매 상태 변경 등)는 해당 유스케이스가 정의될 때 함께 추가한다.

---

### B.2. ProductStatus : Value Object

판매 상태를 나타내는 열거형.

#### 속성

- `ON_SALE` — 판매 중 (사용자에게 노출됨)
- `OFF_SALE` — 판매 중지 (노출되지 않음)
- `HIDDEN` — 숨김 (노출되지 않음, 운영상 사유)

#### 행위

- `isVisibleToUser()` : Boolean — 사용자에게 노출 가능한 상태인지 반환한다. `ON_SALE`만 `true`

---

### B.3. StockQuantity : Value Object

재고 수량을 나타내는 값 객체. 

#### 속성

- `value` : Int — 재고 수량 (0 이상)

**불변식**
- `value`는 0 이상

#### 행위

- `isSoldOut()` : Boolean — 재고가 0인지 반환한다

### B.4. 다른 애그리거트와의 관계

- **Brand** ← `brandId`로 참조 (단방향, ID 참조)
- **ProductLike** → `ProductLike`가 `productId`로 이 애그리거트를 참조함
    - 좋아요 추가/삭제 시 `Product.likeCount` 동기 업데이트 (참고: Product-003)

---

## C. 설계 결정

> 확정된 결정과 보류된 결정을 함께 모은다. 보류된 항목이 결정되면 C.2 → C.1로 이동한다.

### C.1. 확정된 결정

> **왜 이런 선택을 했는지** 보존한다. 결정은 누적될 뿐 수정되지 않는다.

#### Product-002. 품절 상품 정렬 정책 보류
- **일시**: 2026-05-07
- **결정**: 품절 상품을 정렬에서 후순위로 보내지 않고, 일반 정렬에 포함시킴.
- **이유**:
    - 초기 상품 수가 적어 정책 효과가 미미함
    - 리팩토링 비용이 낮음 (정렬 쿼리 한 곳만 수정)
- **영향**: `ORDER BY` 절에 품절 여부 분기 없음.
- **재검토 시점**: 상품 수가 충분히 많아져 품절 상품이 사용자 탐색을 방해할 때, 또는 관련 사용자 피드백 발생 시.

#### Product-003. 좋아요 카운트는 캐시 컬럼으로 보관
- **일시**: 2026-05-07
- **결정**: `Product.likeCount`를 컬럼으로 보관. 좋아요 추가/삭제 시 동기적으로 업데이트.
- **이유**: `likeDesc` 정렬에 사용되므로 매번 COUNT 쿼리는 비효율적.
- **영향**:
    - `ProductLike` 추가/삭제 트랜잭션에서 `Product.likeCount`도 함께 변경
    - 진실의 원천은 `ProductLike` 테이블, `likeCount`는 캐시
- **관련 애그리거트**: ProductLike (참고: ProductLike.md)
- **재검토 시점**: 좋아요 트래픽 증가로 동시성 이슈가 발생하거나 카운트 불일치 문제가 생길 때 → 이벤트 기반 비동기 업데이트로 전환.

### C.2. 보류된 결정

> 아직 결정되지 않았거나 **앞으로 다룰 항목**을 모은다. 결정되면 C.1로 이동한다.

- [ ] 품절 상품 후순위 정렬 (관련: Product-002)
- [ ] 좋아요 카운트 비동기 업데이트 (관련: Product-003)
- [ ] 재고를 별도 애그리거트(Inventory)로 분리
- [ ] 도메인 이벤트 도입 (`ProductRegistered`, `ProductSoldOut` 등)