# 0001. null 안전성은 테스트가 아니라 빌드가 보장한다

**상태** — 적용됨 (2026-09-10)

## 배경

null 안전성을 **빌드가 보장하도록** 한다. 도메인 객체가 주고받는 참조는 기본을 non-null 로 두고,
null 이 허용되는 자리만 명시한다. 위반은 컴파일 단계에서 막는다.

테스트로는 이 보장을 만들 수 없다. 호출 조합마다 케이스를 쌓아야 하고, 새 메서드가 생길 때마다
빠뜨릴 수 있다. **테스트는 빠뜨린 것을 알려주지 않는다.** 반면 타입 수준의 검사는 빠뜨림 자체가
성립하지 않는다 — "이 패키지의 참조는 기본 non-null" 을 선언해두면, 예외를 표시하지 않은 모든
자리가 검사 대상이 된다.

## 검토한 도구

| 도구 | 검사 시점 | 강도 | 도입 비용 |
|---|---|---|---|
| **NullAway** | 컴파일 (Error Prone) | 패키지 기본 non-null, 위반은 컴파일 에러 | `lombok.config` 한 줄 — 설정 한 번으로 끝난다 |
| SpotBugs | 컴파일 후 별도 태스크 | 휴리스틱, 기본값 개념 없음 | exclude filter 를 계속 관리해야 한다 |

도입 비용이 둘 다 Lombok 에서 나오는데, 성격이 다르다.

**NullAway — 설정 한 번.** Lombok 은 컴파일 중에 getter·생성자·`equals` 를 만들어내고,
NullAway 는 Error Prone 에 얹혀 **Lombok 이 손댄 뒤의 결과물**을 본다. 생성된 코드에서 경고가
나면 소스에 그 코드가 없어 고칠 방법이 없다. `lombok.config` 에 다음 한 줄을 넣으면 Lombok 이
생성 메서드마다 `@lombok.Generated` 를 찍고 NullAway 가 건너뛴다.

```
lombok.addLombokGeneratedAnnotation = true
```

`domain` 아래 `@Getter` 가 19개 파일, `@RequiredArgsConstructor` 가 38개 파일에 붙어 있어
범위는 넓지만, 설정은 한 번이면 된다. 현재 리포에 `lombok.config` 자체가 없어 새로 만든다.

**SpotBugs — 유지 비용이 계속 든다.** 바이트코드를 보므로 Lombok 산출물을 그대로 검사한다.
`Order.getLines()` 가 `List<OrderLine>` 을 반환하는 자리는 `EI_EXPOSE_REP`("가변 객체의 참조를
노출한다")에 걸린다. JPA 엔티티에서는 흔하고 의도된 형태인데도 걸린다. 이런 오탐을 한 줄로 끄는
방법이 없어 exclude filter 를 탐지기·클래스 단위로 만들어야 하고, **엔티티가 늘 때마다 다시
손봐야 한다.** 걸러내는 일이 잡아내는 버그보다 많아지면 결과를 아무도 보지 않게 된다.

## 결정 — NullAway

**빌드를 멈춘다.** 위반이 경고가 아니라 컴파일 에러다. 경고는 쌓이면 아무도 안 본다.

**비용이 작다.** Error Prone 에 얹혀 컴파일과 함께 끝난다. SpotBugs 처럼 별도 태스크를
기다리지 않는다.

**기본값이 있다.** 패키지에 `@NullMarked` 하나면 그 아래가 전부 non-null 기준이 된다.
SpotBugs 는 애노테이션이 없는 자리를 그냥 넘어가서, 안 붙인 만큼 조용히 통과한다.

이 리포는 Java 21 / Spring Boot 3.4.4 라 Error Prone 과 NullAway 모두 지원 범위 안이고,
`jacoco` 를 이미 `subprojects` 에 적용하고 있어 빌드 품질 도구를 붙이는 자리가 이미 있다.

## 감수한 것

**경계의 런타임 가드는 그대로 남는다.** `Member.register()` 가 받는 `RegisterCommand` 는
HTTP 요청 JSON 을 Jackson 이 역직렬화한 것이다. 정적 분석은 소스에서 보이는 흐름만 보므로,
런타임에 채워지는 값이 null 일 수 있다는 것은 알 수 없다. `requireNonNull` 도,
`constructorNullCheck` 테스트도 **남긴다.** NullAway 가 막는 것은 "우리 코드끼리 null 을
넘기는 실수" 이지 "밖에서 들어온 null" 이 아니다.

**null 밖의 결함은 여전히 못 잡는다.** 같은 시기에 발견한 `Member.email` 의
`@Embedded` + `@Column` 매핑 결함(컬럼명이 어긋나고 unique 제약이 걸리지 않았다)은
NullAway 도 SpotBugs 도 잡지 못한다. 정적 분석 도구를 넣는 것이 검증 전반을 대신하지 않는다.

**도입 시점의 정리 비용이 든다.** 기존 코드에 `@Nullable` 을 붙여야 통과하는 자리가 나온다.
한 번에 전 패키지를 켜지 않고 `domain` 부터 좁게 시작한다.

## 적용 결과

3단계를 모두 마쳤다. NullAway 는 현재 **ERROR** 로 동작하고 경고 0 건에서 출발한다.
임시 위반을 넣어 빌드가 멈추는 것, 제거하면 통과하는 것을 확인했다.

**설정** — `build.gradle.kts` 와 신규 `lombok.config`

- `net.ltgt.errorprone` 플러그인 + NullAway + JSpecify 애노테이션
- Error Prone 의 다른 검사는 전부 끄고 NullAway 만 켰다
- `AnnotatedPackages = com.loopers` — `domain` 부터 좁게 열어 정리한 뒤 전체로 넓혔다
- **테스트 소스는 검사에서 제외한다.** 일부러 null 을 넣어 가드를 확인하고, `ApiResponse.data`
  같은 optional 필드를 성공 케이스에서 바로 꺼내 쓴다. 그 자리마다 억제를 붙이면 35건이 되는데
  얻는 안전성은 없다. 보장의 대상은 운영 코드다

**계획에 없었으나 필요했던 것 둘**

- `excludedPaths = ".*/build/generated/.*"` — QueryDSL Q 클래스. **Lombok 과 같은 이유**로
  고칠 소스가 없는 생성 코드다. 처음 켰을 때 나온 18건이 전부 여기였다
- `ExcludedFieldAnnotations` — JPA 애노테이션과 `org.mockito.Mock`. Hibernate 와 Mockito 가
  리플렉션으로 채우는 필드라 생성자에서 초기화되지 않는 것이 정상이다. 41건이 18건으로 줄었다

**경고 41건을 이렇게 갈랐다**

| 부류 | 건수 | 처리 |
|---|---|---|
| 프레임워크가 리플렉션으로 주입 | 23 | `ExcludedFieldAnnotations` |
| 진짜 nullable 인데 표시가 없던 필드 | 5 | `@Nullable` — `Order.paidAt`, `Payment.transactionKey`·`approvedAt`·`failureReason`, `BaseEntity.deletedAt` |
| optional 파라미터인데 표시가 없던 자리 | 7 | `@Nullable` — `Payment.approve`, `ApproveCommand.transactionKey` |
| `@Column` 누락 | 3 | 붙임 — `Member.gender`·`passwordHash`, `Email.address` |
| null 가드 자체를 보는 테스트 | 6 | `@SuppressWarnings("NullAway")` — 억제하되 지우지 않는다 |

**범위를 넓히며 드러난 것.** `domain` 만 볼 때는 0건이던 것이 `com.loopers` 전체로 넓히자
운영 코드에서 10건이 더 나왔다. 전부 **비어 있을 수 있는데 그렇게 적혀 있지 않던 값**이다.

- `CoreException.customMessage` — `new CoreException(errorType)` 이면 없다
- `ApiResponse.data` — 실패 응답에는 없다. `Metadata.errorCode`·`message` 는 성공 응답에 없다
- `Payment.fail(reason)` / `markFailed` — `e.getMessage()` 가 null 일 수 있다
- `PayResult`·`PayResponse` 의 `approvedAt`·`failureReason`

**도구가 실제로 찾아준 것.** 억제할 대상이 아니라 **표현이 빠져 있던 자리**가 이렇게 22건이었다.
`payment.approve(null)` 은 POINT 결제의 정상 경로인데 시그니처가 그 사실을 말하지 않고 있었다.
지금은 `@Nullable` 이 말한다.
