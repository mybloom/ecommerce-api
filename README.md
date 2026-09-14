# Ecommerce API

이커머스 핵심 도메인을 구현하고, **애그리거트 경계·동시성 제어·트랜잭션 경계에 대한 판단을 기록으로 남긴** 백엔드 프로젝트입니다.

회원, 포인트, 브랜드, 상품, 좋아요, 주문, 결제 도메인을 레이어드 아키텍처로 구현했습니다.
설계 판단은 코드에 주석으로, 그 근거는 각 기능의 Pull Request 설명에 남겼습니다.

---

## 목차

1. [기술 스택](#1-기술-스택)
2. [아키텍처](#2-아키텍처)
3. [도메인 설계](#3-도메인-설계) — 문서: [도메인 모델 명세](./docs/도메인모델/README.md)
4. [테스트 전략](#4-테스트-전략)
5. [AI 개발 방법](#5-ai-개발-방법) — 문서: [AI 개발 기록](./docs/AI-개발-기록.md)
6. [Pull Request](#6-pull-request) — 링크: [GitHub](https://github.com/mybloom/ecommerce-api/pulls?q=is%3Apr)
7. [실행 방법](#7-실행-방법)

---

## 1. 기술 스택

* **언어/프레임워크** — Java 21, Spring Boot 3.4, Spring Data JPA
* **저장소/빌드** — MySQL, Gradle Kotlin DSL, Docker
* **테스트/정적분석** — JUnit 5, Mockito, AssertJ, Testcontainers, Error Prone + NullAway, pitest, Jacoco

정적 분석은 Error Prone + NullAway 로 **컴파일 단계에서** null 흐름을 검사합니다.
한 번에 ERROR 로 켜면 빌드가 멈추므로 WARN 으로 켜서 위반을 정리한 뒤 ERROR 로 올렸고,
생성 소스와 테스트는 일부러 null 을 넣는 자리라 검사에서 제외했습니다.
→ [build.gradle.kts](./build.gradle.kts)

---

## 2. 아키텍처

Ecommerce API 코드 범위는 `commerce-api` 입니다.

```text
Root
├── apps
│   ├── commerce-api        # 구현 대상
│   └── pg-simulator        # PG 시뮬레이터(PG 연동용)
│
├── modules
│   ├── jpa
│   └── redis
│
└── supports
    ├── jackson
    ├── logging
    └── monitoring
```

* `apps` : 실행 가능한 SpringBootApplication
* `modules` : 특정 구현이나 도메인에 의존하지 않는 reusable configuration
* `supports` : logging, monitoring 같은 부가 기능 add-on

`commerce-api` 내부는 레이어드 아키텍처를 따르고, 의존은 한 방향으로만 흐릅니다.

- 의존은 한 방향으로만 흐르고, infrastructure 는 domain 이 정의한 Repository 인터페이스를 구현하는 어댑터입니다
- 별도 영속성 엔티티를 두지 않고 **도메인 엔티티가 JPA 엔티티를 겸합니다**. 매핑 클래스 2벌과 변환 코드를 없애는 대신 영속성 무지를 포기한 절충입니다


```text
interfaces  →  application  →  domain  ←  infrastructure
```

* `interfaces` : Controller. HTTP 요청/응답 형식만 다룹니다
* `application` : UseCase / Processor. 도메인 서비스를 조율하고 트랜잭션 경계를 잡습니다
* `domain` : 엔티티, 값 객체, 도메인 서비스, Repository 인터페이스
* `infrastructure` : Repository 구현. `domain` 이 정의한 인터페이스를 채웁니다

---

## 3. 도메인 설계

애그리거트별 행위와 구조, 설계 결정의 근거는 [도메인 모델 명세](./docs/도메인모델/README.md)에 남겼습니다.

### 3.1 애그리거트

애그리거트는 서로를 **ID로만 참조**합니다. JPA 연관관계로 묶인 것은 `Order` → `OrderLine` 하나뿐입니다.

```text
Member
Brand
Point        ──memberId──────────────▶  Member
Product      ──brandId───────────────▶  Brand
ProductLike  ──memberId, productId───▶  Member, Product
Order        ──memberId──────────────▶  Member
  └─ OrderLine ──productId───────────▶  Product     
Payment      ──orderId───────────────▶  Order
```

### 3.2 기능

| 애그리거트 | 기능 | 엔드포인트 |
|---|---|---|
| Member | 회원 가입 | `POST /api/v1/members` |
| | 내 정보 조회 | `GET /api/v1/members/me` |
| Brand | 브랜드 조회 | `GET /api/v1/brands/{brandId}` |
| Point | 포인트 충전 | `POST /api/v1/points/charge` |
| | 잔액 조회 | `GET /api/v1/points` |
| Product | 상품 상세 조회 | `GET /api/v1/products/{productId}` |
| ProductLike | 좋아요 | `POST /api/v1/like/products/{productId}` |
| | 좋아요 취소 | `DELETE /api/v1/like/products/{productId}` |
| Order | 주문 | `POST /api/v1/orders` |
| Payment | 결제 | `POST /api/v1/payments` |

---

## 4. 테스트 전략

테스트는 5계층으로 나눠 각 층이 **자기 층의 책임만** 검증하도록 했습니다.

| 계층 | 대상 | 방식 |
|---|---|---|
| 도메인 | 엔티티 · 값 객체의 불변식 | 순수 단위 테스트 |
| 도메인 서비스 | 단일 도메인 규칙 | Mockito |
| Application | 도메인 조율 · 트랜잭션 경계 | `@SpringBootTest` + Testcontainers (MySQL) |
| Controller | 요청 형식 검증 | `@WebMvcTest` |
| E2E | 실제 DB까지 통하는 시나리오 | `@SpringBootTest` + Testcontainers (MySQL) |

각 계층을 왜 해당 방식으로 두었는지는 아래와 같습니다.

**도메인 (엔티티·VO)**

- **방식**: 자기 데이터로 판단되는 규칙이라 컨텍스트 없이 JUnit과 AssertJ만으로 검증합니다.
- **대상**: 생성 불변식, 경계값, 비즈니스 동작을 보고, 값 객체는 동등성 계약과 연산 결과를 함께 봅니다.

**도메인 서비스**

- **방식**: Repository와 Entity를 오가며 흐름을 잇는 일이라 의존성을 채워야 테스트가 성립합니다. 서비스가 아는 것은 도메인 인터페이스뿐이므로 필요한 메서드만 Mockito로 stub해 채우고 컨텍스트는 띄우지 않습니다.
- **대상**: "중복을 확인하고 통과하면 저장한다"는 흐름에서 중복일 때 거절되는지, 통과했을 때 저장이 실제로 일어나고 응답이 정확한지 확인합니다.

**Application Layer**

- **방식**: 여러 도메인 서비스를 엮은 결과가 의도한 상태로 남는지 보는 층입니다. 진짜 트랜잭션 매니저와 실제 DB가 있어야 답할 수 있어 `@SpringBootTest`로 컨텍스트를 띄우고 Testcontainers의 MySQL에 붙입니다. 
- **대상**: "확정이 실패하면 재고 차감도 함께 돌아가는가", "재고 1개를 두 명이 동시에 주문하면 한 건만 확정되는가"를 확인합니다.

**Controller 슬라이스**

- **방식**: Application Layer에 위임하기 전에 요청값이 제대로 들어왔는지만 봅니다. `@WebMvcTest`로 웹 계층만 잘라 띄워 가볍게 유지합니다.
- **대상**: `@Valid` 위반과 필수 헤더 누락이 거절되는지 확인합니다. 정상 케이스는 서비스가 Mock이라 회귀를 잡지 못하므로 E2E에 위임합니다.

**E2E**

- **방식**: 실제 서버를 띄우고 HTTP로 요청해 전 구간을 통과시킵니다.
- **대상**: 핵심 흐름이 끝까지 통과하는지, 도메인 예외가 HTTP 상태 코드까지 의도대로 번역되는지 확인합니다.

작성한 테스트가 실제로 결함을 잡는지는 **코드에 결함을 심어** 확인했습니다. 도메인 레이어는
pitest 가 전수로 심고(변이 170개 중 166개 제거, 살아남은 변이 0), PIT 가 만들지 못하는
인자 교환과 `catch` 블록 변환은 직접 심어 봅니다.

심은 결함을 테스트가 놓치면 프로덕션 코드가 아니라 **테스트를 고쳤습니다.** `ProductLikeFixture` 의
회원·상품 식별자가 둘 다 `1L` 이면 두 인자가 뒤바뀌어도 드러나지 않아 `11L` / `22L` 로 벌렸고,
`save(any())` 로 호출 여부만 보던 단언은 `ArgumentCaptor` 로 저장되는 값까지 확인하도록 바꿨습니다.

```bash
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:pitest   # 도메인 뮤테이션 (Docker 불필요)
```

---

## 5. AI 개발 방법

이 프로젝트는 Claude Code 로 개발했습니다. 모델에게 규칙을 주고, 확정된 규칙은 도구가 강제하게 해 결정성을 높였습니다.

1) 규칙을 먼저 세웁니다
2) 레이어 단위로 개발하고 레이어마다 테스트를 먼저 씁니다
3) 테스트가 진짜로 결함을 잡는지 따로 검증합니다
4) 규칙 준수를 모델의 판단에 두지 않습니다

각 항목을 구체적으로 어떻게 했는지는 [AI 개발 기록](./docs/AI-개발-기록.md)에 남겼습니다.

---

## 6. Pull Request

기능별 배경과 변경 사항, 그때 내린 기술적 판단은 [Pull Requests](https://github.com/mybloom/ecommerce-api/pulls?q=is%3Apr) 에 남겼습니다.

---

## 7. 실행 방법

**JDK 21**, **Docker** 가 필요합니다.

```bash
docker-compose -f ./docker/infra-compose.yml up   # 인프라 (local 프로필)
./gradlew test
./gradlew build
```

모니터링이 필요하면 아래를 띄우고 **http://localhost:3000** 에 `admin` / `admin` 으로 접속합니다.

```bash
docker-compose -f ./docker/monitoring-compose.yml up
```
