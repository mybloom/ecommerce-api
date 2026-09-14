# Controller 컨벤션

`interfaces` 레이어의 REST 컨트롤러 작성 가이드.

HTTP 요청을 받아 UseCase를 호출하고 응답을 만든다. 비즈니스 로직은 포함하지 않으며, 모든 예외 처리는 `@RestControllerAdvice`로 위임한다.

```
com.loopers.interfaces.api.point.PointV1Controller    ← 이 문서가 다루는 영역
com.loopers.application.point.PointUseCase             ← UseCase
```

## 1. 책임

컨트롤러의 책임:
- HTTP 요청 수신 (URL, 메서드, 헤더, 본문, 쿼리 파라미터 매핑)
- 요청 검증 (`@Valid` Bean Validation)
- Request → Info DTO 변환
- UseCase 호출
- Result → Response DTO 변환
- 응답 래핑 (`ApiResponse.success(...)`)

컨트롤러가 하지 않는 것:
- 비즈니스 규칙 검증 — 도메인/UseCase 책임
- try-catch로 예외 처리 — `@RestControllerAdvice`에 위임
- 트랜잭션 관리 — UseCase 책임
- 도메인 객체 직접 노출 — Response DTO로 변환

## 2. 클래스 구조: 인터페이스 + 구현체 분리

API 스펙은 인터페이스(`XxxV1ApiSpec`)로 분리하고, 컨트롤러는 그 인터페이스를 구현한다.

### 인터페이스: API 스펙 + Swagger 문서화

```java
@Tag(name = "Point V1 API", description = "Point management operations")
public interface PointV1ApiSpec {

    @Operation(summary = "포인트 충전", description = "헤더 X-MEMBER-ID 로 식별된 사용자의 포인트를 충전합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "포인트 충전 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    ApiResponse<PointV1Dto.ChargeResponse> charge(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId,
            PointV1Dto.ChargeRequest chargeRequest
    );
}
```

인터페이스가 가져가는 것:
- `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter` 등 Swagger 어노테이션
- 메서드 시그니처와 파라미터 의미

### 구현체: HTTP 매핑 + UseCase 호출

```java
@RequiredArgsConstructor
@RequestMapping("/api/v1/points")
@RestController
public class PointV1Controller implements PointV1ApiSpec {

    private final PointUseCase pointUseCase;

    @PostMapping("/charge")
    @Override
    public ApiResponse<PointV1Dto.ChargeResponse> charge(
            @RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId,
            @Valid @RequestBody PointV1Dto.ChargeRequest request
    ) {
        PointUseCaseDto.ChargeResult result = pointUseCase.charge(request.toInfo(memberId));
        return ApiResponse.success(PointV1Dto.ChargeResponse.from(result));
    }
}
```

구현체가 가져가는 것:
- `@RestController`, `@RequestMapping`
- `@PostMapping`, `@GetMapping` 등 HTTP 메서드 매핑
- `@RequestBody`, `@RequestHeader`, `@PathVariable` 등 요청 매핑
- `@Valid` 검증
- 실제 호출 로직 (한 줄에서 두 줄)

### 분리하는 이유

- 스펙(인터페이스)이 명확히 분리되어 API 문서가 한 곳에 모임
- 구현 변경이 스펙에 영향을 주지 않음
- Swagger 문서가 인터페이스만 봐도 완성됨

## 3. 어노테이션과 의존성 주입

```java
@RequiredArgsConstructor
@RequestMapping("/api/v1/points")
@RestController
public class PointV1Controller implements PointV1ApiSpec {
    private final PointUseCase pointUseCase;
}
```

- `@RestController`: REST API 컨트롤러
- `@RequestMapping("/api/v1/...")`: 베이스 URL. 버전 관리 위해 `/api/v1/` 접두사 사용
- `@RequiredArgsConstructor`: 생성자 주입
- 필드는 `final`

## 4. URL 컨벤션

베이스 경로는 `/api/v{version}/{resource}` 형태. 메서드별 추가 경로는 행위에 따라 RESTful 또는 RPC 스타일을 선택.

```java
// RESTful 스타일
@PostMapping              // POST /api/v1/members
@GetMapping("/me")        // GET /api/v1/members/me

// RPC 스타일 (특정 행위가 자원에 매핑하기 어려울 때)
@PostMapping("/charge")   // POST /api/v1/points/charge
@GetMapping               // GET /api/v1/points
```

팀 룰에 따라 일관되게. 두 스타일이 한 도메인 안에서 섞이지 않도록 주의한다.

## 5. 요청 매핑

### `@RequestBody`는 항상 `@Valid`와 함께

JSON 본문을 받을 때는 **반드시 `@RequestBody`를 명시**하고, **항상 `@Valid`를 붙인다**.

```java
public ApiResponse<PointV1Dto.ChargeResponse> charge(
        @RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId,
        @Valid @RequestBody PointV1Dto.ChargeRequest request   // ← @RequestBody + @Valid
) { ... }
```

`@RequestBody` 없으면 Spring이 폼/쿼리 바인딩으로 시도하여 JSON 파싱이 안 된다 (record는 setter가 없어 모든 필드가 null이 됨).

`@Valid`가 없으면 Request DTO의 Bean Validation 어노테이션이 동작하지 않는다. 지금 검증 어노테이션이 없더라도 미래 추가에 대비해 항상 붙인다.

### 헤더로 사용자 식별

`X-MEMBER-ID` 헤더로 사용자를 식별한다.

```java
@RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId
```

`required = true`로 두면 헤더 누락 시 `MissingRequestHeaderException`이 발생하고, `@RestControllerAdvice`가 400으로 매핑한다.

## 6. Request DTO: 검증 + 변환

### Bean Validation 어노테이션 사용

`Request` record의 필드에 검증 어노테이션을 단다.

```java
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 15) String loginId,
        @NotBlank @Email String email,
        @NotNull LocalDate birthDate,
        @NotNull MemberUseCaseDto.Gender gender,
        @NotBlank @Size(min = 4, max = 50) String password
) { ... }
```

자주 쓰는 어노테이션:
- `@NotNull`: null 금지
- `@NotBlank`: 빈 문자열과 공백도 금지 (String 전용)
- `@Size(min, max)`: 길이 제한
- `@Email`: 이메일 형식
- `@Min`, `@Max`: 숫자 범위
- `@Pattern(regexp = "...")`: 정규식

#### 값의 존재는 반드시 따로 명시한다

**`@Size` `@Email` `@Min` `@Pattern` 은 값이 있을 때만 검사한다.** null 이면 검사할 것이
없어 그냥 통과시킨다. 값이 반드시 있어야 한다는 것은 `@NotNull`·`@NotBlank` 가 따로 말해야 한다.

```java
@Size(min = 3, max = 15) String loginId        // ❌ null 이 통과한다
@NotBlank @Size(min = 3, max = 15) String loginId   // ✅
```

- 문자열 → **`@NotBlank`** (`" "` 를 허용할 이유가 거의 없다)
- 그 외(`Long`, `LocalDate`, enum ...) → **`@NotNull`**

빠뜨리면 잘못된 요청이 도메인까지 내려간다. 실제로 `{"email": null}` 이 `Email.of(null)` 까지
가서 `matcher(null)` 에서 NPE 가 나고 **500** 으로 응답한 적이 있다.

#### enum 값을 받는 필드는 enum 타입으로 선언한다

`String` 으로 받아 `valueOf` 로 바꾸면, enum 에 없는 값이 `IllegalArgumentException` 이 되어
**500** 으로 나간다. `@NotBlank` 는 빈 값만 막고 `"X"` 는 통과시킨다.

```java
@NotBlank String gender                 // ❌ "X" 가 통과해 valueOf 에서 터진다
@NotNull MemberUseCaseDto.Gender gender // ✅
```

타입을 enum 으로 두면 Jackson 이 역직렬화 단계에서 걸러내고, `ApiControllerAdvice` 의
`InvalidFormatException` 핸들러가 **사용 가능한 값 목록까지 담아** 400 을 낸다.

```json
{"message":"필드 'gender'의 값 'X'이(가) 예상 타입(Gender)과 일치하지 않습니다. 사용 가능한 값 : [MALE, FEMALE]"}
```

### `toInfo(...)` 메서드로 UseCase Info DTO로 변환

Request는 자기 자신을 UseCase의 Info로 변환하는 메서드를 가진다.

```java
public record ChargeRequest(
        @NotNull @Min(1) Long amount
) {
    public PointUseCaseDto.ChargeInfo toInfo(Long memberId) {
        return new PointUseCaseDto.ChargeInfo(memberId, amount);
    }
}
```

헤더에서 받은 `memberId`처럼 본문 외부의 값이 필요하면 메서드 인자로 받는다.

## 7. Response DTO: 정적 팩토리 변환

UseCase의 Result를 받아 Response로 변환한다. `from(...)` 정적 팩토리 사용.

```java
public record ChargeResponse(
        Long memberId,
        Long balance
) {
    public static ChargeResponse from(PointUseCaseDto.ChargeResult result) {
        return new ChargeResponse(result.memberId(), result.balance());
    }
}
```

UseCase 컨벤션과 동일하게 `from(input)`이 한 입력으로 변환 가능할 때만 사용한다 ([usecase.md](./usecase.md) "from()이 동작하지 않는 경우" 참고).

## 8. DTO 컨테이너 클래스 패턴

한 컨트롤러의 모든 DTO를 하나의 클래스(`XxxV1Dto`)에 모아두고, 안에 record로 정의한다.

```java
public class PointV1Dto {

    public record ChargeRequest(@NotNull @Min(1) Long amount) {
        public PointUseCaseDto.ChargeInfo toInfo(Long memberId) { ... }
    }

    public record ChargeResponse(Long memberId, Long balance) {
        public static ChargeResponse from(PointUseCaseDto.ChargeResult result) { ... }
    }

    public record RetrieveResponse(Long memberId, Long balance) {
        public static RetrieveResponse from(PointUseCaseDto.RetrieveResult result) { ... }
    }
}
```

UseCase의 `XxxUseCaseDto` 패턴과 동일한 구조.

## 9. 응답 래핑: `ApiResponse`

모든 응답은 `ApiResponse<T>`로 감싼다. 컨트롤러에서는 `ApiResponse.success(...)`만 사용한다 (실패 응답은 `@RestControllerAdvice`가 만든다).

```java
return ApiResponse.success(PointV1Dto.ChargeResponse.from(result));
```

응답 JSON 구조:
```json
{
  "meta": { "result": "SUCCESS" },
  "data": { ... }
}
```

실패 시:
```json
{
  "meta": { "result": "FAIL", "errorCode": "...", "errorMessage": "..." },
  "data": null
}
```

## 10. 예외 처리: `@RestControllerAdvice`에 위임

**컨트롤러에는 try-catch가 없다.** 모든 예외는 글로벌 핸들러(`ApiControllerAdvice`)가 처리한다.

핸들러가 다루는 주요 예외:

| 예외 | HTTP Status | 매핑 |
|---|---|---|
| `MethodArgumentNotValidException` (`@Valid` 실패) | 400 | 어떤 필드가 왜 실패했는지 메시지 생성 |
| `MissingRequestHeaderException` | 400 | `X-MEMBER-ID` 누락 등 |
| `HttpMessageNotReadableException` | 400 | JSON 파싱 실패 (잘못된 enum, 필드 타입 mismatch 등) |
| `MethodArgumentTypeMismatchException` | 400 | 쿼리/패스 파라미터 타입 불일치 |
| `MissingServletRequestParameterException` | 400 | 필수 쿼리 파라미터 누락 |
| `NoResourceFoundException` | 404 | 정적 리소스 못 찾음 |
| `CoreException` | `e.getErrorType().getStatus()` | 도메인/UseCase 예외 |
| `Throwable` (그 외 모든 예외) | 500 | 최후 안전망, 로그 + INTERNAL_ERROR |

### 컨트롤러는 그냥 throws 없이

```java
// ✅ 예외를 신경쓰지 않음
@PostMapping("/charge")
public ApiResponse<PointV1Dto.ChargeResponse> charge(...) {
    PointUseCaseDto.ChargeResult result = pointUseCase.charge(request.toInfo(memberId));
    return ApiResponse.success(PointV1Dto.ChargeResponse.from(result));
}

// ❌ 컨트롤러에서 try-catch
@PostMapping("/charge")
public ApiResponse<PointV1Dto.ChargeResponse> charge(...) {
    try {
        // ...
    } catch (CoreException e) {
        // 컨트롤러에서 직접 처리하지 않음
    }
}
```

컨트롤러는 행복 경로(happy path)만 코드에 표현하고, 예외 흐름은 핸들러에 일임한다.

## 11. 테스트: 슬라이스 vs E2E

컨트롤러는 두 종류의 테스트로 검증한다.

### `@WebMvcTest`: HTTP 계약 슬라이스 테스트

웹 계층만 띄워 빠르게 HTTP 계약을 검증. UseCase는 `@MockitoBean`으로 격리.

```java
@WebMvcTest(PointV1Controller.class)
class PointV1ControllerTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private PointUseCase pointUseCase;

    @Autowired
    public PointV1ControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }
}
```

### `@WebMvcTest`에서 검증하는 것

- **`@Valid` 위반 → 400**

  ```java
  @Test
  @DisplayName("회원가입 요청에 loginId가 블랭크이면 400 Bad Request")
  void register_validation_blank_loginId() throws Exception {
      MemberV1Dto.registerRequest invalidRequest = MemberFixture.aregisterRequestWithLoginId("");

      mockMvc.perform(post("/api/v1/members")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(invalidRequest)))
              .andExpect(status().isBadRequest());
  }
  ```

- **헤더 누락 → 400**

  ```java
  @Test
  @DisplayName("X-MEMBER-ID 헤더가 없으면 400을 응답한다")
  void returns400_whenMemberIdHeaderMissing() throws Exception {
      mockMvc.perform(get("/api/v1/points"))
              .andExpect(status().isBadRequest());
  }
  ```

- **UseCase 호출 인자 매핑 검증** (`argThat` 사용)

  ```java
  @Test
  @DisplayName("헤더의 memberId와 요청 바디가 UseCase에 올바르게 전달된다")
  void passesMemberIdAndAmountToUseCase() throws Exception {
      when(pointUseCase.charge(any(PointUseCaseDto.ChargeInfo.class)))
              .thenReturn(new PointUseCaseDto.ChargeResult(1L, 500L, 500L));

      mockMvc.perform(post("/api/v1/points/charge")
                      .header("X-MEMBER-ID", 1L)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""{"amount": 500}"""))
              .andExpect(status().isOk());

      verify(pointUseCase).charge(argThat(info ->
              info.memberId().equals(1L) && info.amount() == 500L
      ));
  }
  ```

  이 검증이 빠지면 "헤더 값이 무시되어도" 테스트가 통과한다. 컨트롤러의 핵심 책임(요청 → UseCase 인자 매핑)을 검증하는 테스트다.

- **UseCase가 던진 예외 → HTTP 매핑**

  ```java
  @Test
  @DisplayName("UseCase가 NOT_FOUND 예외를 던지면 404를 응답한다")
  void returns404_whenUseCaseThrowsNotFound() throws Exception {
      when(pointUseCase.charge(any())).thenThrow(
              new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

      mockMvc.perform(post("/api/v1/points/charge")
                      .header("X-MEMBER-ID", 1L)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""{"amount": 1000}"""))
              .andExpect(status().isNotFound());
  }
  ```

### `@WebMvcTest`에서 검증하지 않는 것

- DB 상태 변경 → DB 자체가 없음
- UseCase 비즈니스 로직 → mock이라 동작 안 함
- 다중 요청 간 일관성 (충전 후 조회 등) → E2E의 영역

### `@SpringBootTest(RANDOM_PORT)`: E2E 테스트

진짜 HTTP + 진짜 DB로 모든 계층이 함께 동작하는지 확인.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PointV1ApiE2ETest {
    private final TestRestTemplate testRestTemplate;
    private final MemberRepository memberRepository;
    private final PointRepository pointRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public PointV1ApiE2ETest(...) { ... }

    @BeforeEach
    void setUp() {
        this.member = memberRepository.save(MemberFixture.aMember());
        pointRepository.save(PointFixture.anInitialPoint(member.getId()));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }
}
```

### E2E에서 검증하는 것

- **Happy path**: 정상 요청 → 200 + 응답 DTO 값
- **다중 요청 간 일관성**: 충전 후 조회에서 잔액 누적 확인
- **핵심 회귀 보호**: 헤더 누락, 존재하지 않는 회원 등 결정적인 흐름

```java
@Test
@DisplayName("포인트 충전이 성공할 경우, 충전된 포인트 정보를 응답으로 반환한다.")
void returnPointInfo_whenChargeIsSuccessful() {
    Long chargeAmount = 1000L;
    PointV1Dto.ChargeRequest request = new PointV1Dto.ChargeRequest(chargeAmount);

    ParameterizedTypeReference<ApiResponse<PointV1Dto.ChargeResponse>> responseType =
            new ParameterizedTypeReference<>() {};
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-MEMBER-ID", member.getId().toString());

    ResponseEntity<ApiResponse<PointV1Dto.ChargeResponse>> response =
            testRestTemplate.exchange("/api/v1/points/charge", HttpMethod.POST,
                    new HttpEntity<>(request, headers), responseType);

    assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().memberId()).isEqualTo(member.getId()),
            () -> assertThat(response.getBody().data().balance()).isEqualTo(chargeAmount)
    );
}
```

### E2E의 셋업: Repository 직접 사용 OK

UseCase 테스트와 달리 E2E는 **검증할 API 행위에 집중**한다. 셋업은 빠르고 단순한 게 좋다 — Repository로 직접 박아넣는다.

```java
@BeforeEach
void setUp() {
    this.member = memberRepository.save(MemberFixture.aMember());
    pointRepository.save(PointFixture.anInitialPoint(member.getId()));
}
```

UseCase 테스트는 도메인 흐름을 따라가는 게 원칙이지만 ([usecase.md](./usecase.md) "픽스처와 셋업 패턴" 참고), E2E는 트레이드오프가 다르다.

### 응답 파싱: `Map`이 아닌 DTO 사용

`ParameterizedTypeReference`로 제네릭 타입을 보존해 DTO로 받는다.

```java
ParameterizedTypeReference<ApiResponse<PointV1Dto.ChargeResponse>> responseType =
        new ParameterizedTypeReference<>() {};

ResponseEntity<ApiResponse<PointV1Dto.ChargeResponse>> response =
        testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, httpEntity, responseType);

PointV1Dto.ChargeResponse data = response.getBody().data();
```

`Map<String, Object>` 대신 DTO를 쓰면:
- 타입 안전 (캐스팅 없음)
- IDE 자동완성
- 응답 스키마 변경 시 컴파일 에러로 즉시 감지

### 검증 분담 정리

| 검증 항목 | `@WebMvcTest` | E2E |
|---|---|---|
| `@Valid` 위반 | ✅ | △ (한 번 정도) |
| 헤더 누락 | ✅ | △ |
| UseCase 호출 인자 매핑 (`argThat`) | ✅ | ❌ (UseCase 직접 호출 검증 불가) |
| UseCase 예외 → HTTP 매핑 | ✅ (mock으로) | ✅ (실제 흐름으로) |
| Happy path 응답 DTO | △ (mock 응답) | ✅ |
| DB 상태 변경 | ❌ | △ (직접 검증보다는 다음 API로) |
| 다중 요청 간 일관성 | ❌ | ✅ |

## 12. 금지 사항

- 컨트롤러에서 try-catch 사용 (예외는 `@RestControllerAdvice`로)
- 컨트롤러에서 비즈니스 로직 작성
- `@RequestBody` 누락 (record가 모든 필드 null로 채워짐)
- `@RequestBody`에 `@Valid` 누락
- 도메인 엔티티(`Point`, `Member`)를 응답으로 그대로 반환
- UseCase의 Info/Result를 컨트롤러 응답에 그대로 노출 — Response DTO로 한 번 더 감싼다
- 필드 주입 (`@Autowired private XxxUseCase ...`)
