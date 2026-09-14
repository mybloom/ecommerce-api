---
name: test-effectiveness
description: 작성한 테스트가 실제로 결함을 잡아내는지 검증한다. 두 시점에 돈다 — tfd-workflow-outside-in 3단계(Domain) 종료 직후에는 pitest 로 도메인을 전수 검증하고, 5단계(E2E 봉합) 직후 기능 커밋 직전에는 pitest 가 닿지 않는 application·interfaces·예외 변환 경로를 손으로 검증한다. 테스트를 새로 작성했거나 크게 고쳤을 때, 그리고 "테스트가 제대로 된 건지 확인", "테스트 실효성", "뮤테이션" 같은 요청에 사용한다.
---

# 테스트 실효성 검증

테스트가 초록불이라는 사실은 그 테스트가 결함을 잡아낸다는 뜻이 아니다. 커버리지 숫자도
마찬가지다 — 실행은 되지만 아무것도 단언하지 않는 테스트는 커버리지에 그대로 잡힌다.

이 스킬은 프로덕션 코드에 일부러 결함을 심어 **테스트 쪽을 검증한다**. 검증 대상이 프로덕션
코드가 아니라 테스트 코드라는 점에서 방향이 반대다.

## 언제 실행하는가

**두 시점으로 나뉘고, 앞의 것은 도구가 한다.**

| 시점 | 무엇으로 | 대상 |
|---|---|---|
| `tfd-workflow-outside-in` **3단계(Domain) 종료 직후** | `./gradlew :apps:commerce-api:pitest` | `com.loopers.domain.*` |
| **5단계(E2E 봉합) 직후, 기능 커밋 직전** | 이 스킬 — 손으로 심는다 | pitest 밖: `application` · `interfaces` · 예외 변환 경로 |

### 도메인은 손으로 심지 않는다

pitest 가 바이트코드에 변이를 넣고 그 줄을 덮는 테스트만 골라 돌린다. **소스를 건드리지 않으므로
원복 누락 위험이 구조적으로 없다** — 2절이 *"이 작업의 유일한 실질적 위험"* 이라고 적은 그것이 사라진다.

도메인 테스트는 스프링 컨텍스트 없이 돌아 전체가 20초 안에 끝난다. 3단계는 밖에서 안으로 만들 때
**처음으로 진짜 초록불이 오는 구간**이라 여기서 돌리는 것이 가장 싸다.

### 손으로 심는 것은 pitest 밖이다

`application` 과 `interfaces` 는 `@SpringBootTest` · `@WebMvcTest` 위에 있어 변이마다
테스트컨테이너가 뜬다. 그래서 pitest 대상에서 뺐고, 이쪽은 손으로 한다.

**전체가 초록불이 된 뒤에 시작한다.** 빨간불이 남아 있으면 심은 결함 때문에 실패한 것인지
원래 실패하던 것인지 구별할 수 없다.

**대상은 이번 기능이 건드린 코드로 좁힌다.** 전수 조사가 아니다.

```bash
git diff main...HEAD -- apps/commerce-api/src/main
```

## 1. 어디에 심을지 고른다

전수 조사가 목적이 아니다. 아래 네 곳은 실수가 실제로 자주 나면서 테스트가 놓치기도 쉬운
지점이라 우선순위가 높다.

**네 곳 중 둘은 pitest 가 이미 덮는다.** 나머지 둘은 PIT 기본 뮤테이터에 없어서 손으로 심어야 한다.

| 지점 | 심는 방법 | 잡아내려는 것 | 누가 |
|---|---|---|---|
| 같은 타입의 인접 인자 | 두 인자를 서로 뒤바꾼다 | 컴파일러가 못 잡는 자리. `like(Long memberId, Long productId)`처럼 타입이 같으면 뒤바뀌어도 빌드가 통과한다 | **손** — PIT 기본에 인자 교환이 없다 |
| 예외 처리 경로 | `catch` 블록을 `throw e`로 바꾼다 | 예외 변환·복구 로직이 정말 필요한지, 그 자리가 맞는지 | **손** — PIT 기본에 없고, 이 프로젝트에선 주로 application 에 있다 |
| 상태를 바꾸는 메서드 | 본문을 통째로 비운다 | 부수효과가 실제로 일어나는지 아무도 안 보고 있는 경우 | 도메인이면 pitest (`removed call to`) |
| 가드 조건문 | 조건을 제거하거나 반전시킨다 | 분기 양쪽이 모두 검증되고 있는지 | 도메인이면 pitest (`negated conditional` · `changed conditional boundary`) |

## 2. 하나씩 심고 원복한다

여기부터는 **손으로 심을 때의 절차다.** pitest 가 도는 구간에는 해당하지 않는다.

**한 번에 하나만 심는다.** 둘 이상을 동시에 심으면 어느 테스트가 무엇을 잡았는지 알 수 없고,
원복 누락 위험도 커진다.

1. 결함 하나를 심는다
2. 관련 테스트만 골라 실행한다 (전체를 돌릴 필요 없다 — 아래 명령어 참고)
3. **빨간불을 확인한다**
4. 즉시 원복한다

마지막에 반드시 `git diff`로 프로덕션 코드에 잔존 결함이 없는지 확인한다. 심은 결함을 원복하지
않고 커밋하는 것이 이 작업의 유일한 실질적 위험이다.

## 3. 통과해버렸으면(survived) 테스트를 고친다

결함을 심었는데 테스트가 초록불이면 **테스트가 잘못된 것이다.** 프로덕션 코드를 바꾸지 말고
테스트를 고친다. 실제로 자주 나오는 세 가지:

**픽스처 상수가 서로 같다**
```java
// 뒤바뀜이 드러나지 않는다
public static final Long DEFAULT_MEMBER_ID = 1L;
public static final Long DEFAULT_PRODUCT_ID = 1L;

// 값을 벌린다
public static final Long DEFAULT_MEMBER_ID = 11L;
public static final Long DEFAULT_PRODUCT_ID = 22L;
```

**`any()`로 호출 여부만 본다**
```java
// 무엇이 저장되는지는 보지 않는다
verify(repository, times(1)).save(any(ProductLike.class));

// 저장되는 값을 직접 단언한다
ArgumentCaptor<ProductLike> captor = ArgumentCaptor.forClass(ProductLike.class);
verify(repository, times(1)).save(captor.capture());
assertThat(captor.getValue().getMemberId()).isEqualTo(DEFAULT_MEMBER_ID);
```

**상태 변화를 한 번만 확인한다**

`0 → 1`만 검증하면 누적으로 증가시키는 코드와 그냥 `1`을 덮어쓰는 코드를 구별하지 못한다.
여러 번 호출해 누적되는지 보는 케이스를 추가한다.

## 4. PR에는 결과만 쓴다

**절차와 "잡아낸 목록"은 PR에 쓰지 않는다.** 리뷰어가 그 정보로 할 수 있는 일이 없다.

**고친 결과는 쓴다.** 테스트 코드 변경이 diff에 남으므로 — 픽스처 상수가 갑자기 바뀌거나
`any()`가 `ArgumentCaptor`로 바뀌면 — 리뷰어는 "왜?"를 묻게 된다. 3~5줄로 답한다.

```markdown
주요 지점에 일부러 결함을 심어 해당 테스트가 실패하는지 확인했다. 그대로 통과해버린
테스트 세 개를 아래처럼 고쳤다.

- `XxxFixture`의 두 상수가 같아 뒤바뀜이 드러나지 않았다 → 값을 벌렸다
- `save(any())`로 호출 여부만 봤다 → `ArgumentCaptor`로 내용을 단언한다
```

## 명령어

Gradle 멀티모듈 프로젝트다.

```bash
# 도메인 뮤테이션 — 3단계 종료 직후. 20초 안에 끝난다
./gradlew :apps:commerce-api:pitest
# 리포트: apps/commerce-api/build/reports/pitest/index.html

# 단일 테스트 클래스
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.productlike.ProductLikeServiceTest"

# 전체
./gradlew :apps:commerce-api:test

# 원복 확인 — 프로덕션 코드에 심은 결함이 남아 있지 않은지
git diff -- apps/commerce-api/src/main
```

`@SpringBootTest` 계열은 Testcontainers로 MySQL·Redis를 직접 띄우므로 Docker 데몬이
실행 중이어야 한다. **pitest 는 도메인만 대상으로 해서 Docker 없이 돈다.**

### pitest 리포트에서 볼 것

| 숫자 | 뜻 | 어떻게 대응하나 |
|---|---|---|
| **SURVIVED** | 테스트가 있는데 못 잡았다 | **테스트가 잘못된 것이다.** 3절대로 테스트를 고친다 |
| **NO_COVERAGE** | 그 줄을 덮는 테스트가 아예 없다 | 실효성 문제가 아니라 커버리지 문제다. 테스트를 새로 쓸지 판단한다 |
| **테스트 강도(test strength)** | 커버된 것 중 몇 %를 잡았나 | **100%를 유지한다.** 이것이 이 스킬이 지키려는 숫자다 |
| 뮤테이션 스코어 | 전체 중 몇 %를 잡았나 | 커버리지를 따라 움직인다. 강도와 같이 봐야 뜻이 있다 |

임계값(`mutationThreshold`)은 아직 걸려 있지 않다. 빌드가 막아 주지 않으므로 **리포트를 직접 본다.**
