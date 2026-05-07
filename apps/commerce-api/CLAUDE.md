# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All commands are run from the **repo root** (`commece-api-2604/`), not from `apps/commerce-api/`.

```bash
# Start required infrastructure (MySQL, Redis) before running the app or tests
docker-compose -f ./docker/infra-compose.yml up -d

# Build the entire project
./gradlew build

# Run all tests for this module only
./gradlew :apps:commerce-api:test

# Run a single test class
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.point.PointServiceTest"

# Run a single test method
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.point.PointServiceTest.Register.saveMember_whenCreateSucceeds"

# Run the application (local profile is default)
./gradlew :apps:commerce-api:bootRun
```

Tests run with `spring.profiles.active=test` and `user.timezone=Asia/Seoul` automatically (configured in root `build.gradle.kts`). `maxParallelForks = 1` so tests are sequential.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when running locally.

## Architecture

This is a **multi-module Gradle project**. Module hierarchy:

- `apps/` — runnable Spring Boot applications (`commerce-api`, `commerce-batch`, `commerce-streamer`)
- `modules/` — reusable infrastructure configs (`jpa`, `redis`, `kafka`)
- `supports/` — add-on modules (`jackson`, `logging`, `monitoring`)

`apps` depend on `modules` and `supports`. `modules` and `supports` are not runnable (their `BootJar` tasks are disabled).

### Package layers inside `commerce-api`

```
com.loopers
├── interfaces.api.*        # Controllers (HTTP layer)
├── application.*           # UseCases (orchestration + transaction boundary)
├── domain.*                # Domain objects, Services, Repository interfaces
├── infrastructure.*        # JPA repository implementations
└── support.error           # CoreException, ErrorType
```

Data flows inward and upward: `interfaces → application → domain ← infrastructure`.

### Request flow

```
Request DTO → toInfo() → UseCaseDto.Info
  → UseCase.method() → ServiceDto.Command
  → DomainService.method() → domain object / ServiceDto.Query
  → UseCaseDto.Result.from()
  → Response DTO.from()
  → ApiResponse.success(...)
```

Each layer has its own DTO container class (`XxxV1Dto`, `XxxUseCaseDto`, `XxxServiceDto`), all defined as inner `record`s inside a single outer class. Conversion is always done via `toInfo()` / `toCommand()` instance methods going down and `from()` static factories going up.

### Key conventions

**Domain objects** (`domain.*`)
- Entities use protected no-arg constructor + static factory (`create`, `createInitial`)
- VOs are immutable with `@EqualsAndHashCode`; all validation happens in the `of()` factory; arithmetic uses `Math.addExact`/`Math.subtractExact`
- Domain method contract: validate all inputs first, then mutate state (no partial state)
- Commands are `void`; queries use getters (CQS inside domain objects)
- No Spring annotations inside `domain.*` (JPA mapping annotations are allowed)
- `BAD_REQUEST` = the input value itself is invalid; `CONFLICT` = input is valid but conflicts with current state

**Domain services** (`domain.*.XxxService`, `@Service`)
- Trust domain objects; don't re-validate rules the domain already enforces
- Never catch domain exceptions and re-throw as a different `ErrorType`
- Input is always a `Command`; output is either the domain object directly or a `Query` record

**UseCases** (`application.*.XxxUseCase`, `@Component`)
- Owns the `@Transactional` boundary (use `org.springframework.transaction.annotation.Transactional`)
- Combines multiple domain services; validates cross-domain existence (e.g., member must exist before charging points)
- Never expose domain DTOs or entities past the UseCase boundary

**Controllers** (`interfaces.api.*.XxxV1Controller`)
- Split into `XxxV1ApiSpec` (interface, holds all Swagger annotations) and `XxxV1Controller` (implementation)
- Always pair `@RequestBody` with `@Valid`
- User identity is passed via `X-MEMBER-ID` header (`@RequestHeader(name = "X-MEMBER-ID", required = true)`)
- No try-catch; all exception handling is in `ApiControllerAdvice`
- All responses are wrapped in `ApiResponse<T>`; failure responses are built exclusively by `ApiControllerAdvice`

**Dependency injection**: always constructor injection with `@RequiredArgsConstructor` and `final` fields. Never `@Autowired` field injection.

### Testing strategy

| Test class type | Annotation | Purpose |
|---|---|---|
| Domain object tests | plain JUnit (no Spring) | Unit-test entity/VO behaviour |
| Domain service tests | `@ExtendWith(MockitoExtension.class)` | Mockito mocks for repos; manual constructor injection (never `@InjectMocks`) |
| UseCase tests | `@SpringBootTest` | Full Spring context + real DB via Testcontainers; `@MockitoSpyBean` to force partial failure for rollback tests |
| Controller slice tests | `@WebMvcTest` | Validates HTTP contract (`@Valid`, header, routing); UseCase is `@MockitoBean` |
| E2E tests | `@SpringBootTest(RANDOM_PORT)` | Full stack with `TestRestTemplate`; direct repo setup in `@BeforeEach` |

Every integration/E2E test calls `databaseCleanUp.truncateAllTables()` in `@AfterEach`. `DatabaseCleanUp` is a test-fixture class provided by `modules/jpa`.

Fixture classes are named `XxxFixture`, use `a-`/`an-` prefixed factory methods (e.g., `aPointWithBalance(1000L)`), and live either in `src/test` of the app or in the `testFixtures` source set of a module (importable via `testFixtures(project(":modules:jpa"))`).

Exception assertions always verify `ErrorType` (enum, compile-safe), not message substrings.

Use `when().thenReturn()` (classic Mockito) throughout; do not mix BDD `given().willReturn()` style.
