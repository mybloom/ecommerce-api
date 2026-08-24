# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

It covers **how to run things** and **how the repo is laid out**. Coding rules and the development
process live in skills — see below. Keep it that way so the two don't drift apart.

## Rules and workflow live in skills

| Where | What |
|---|---|
| `.claude/skills/architecture-rules/` | Layer dependencies, package naming, DTO conversion chain, exception handling (`CoreException` / `ErrorType`), Lombok policy, test conventions, reference implementations |
| `.claude/skills/tfd-workflow/` | How to add a feature: one layer at a time, test first, stop for review between layers |
| `docs/도메인모델/` | Domain specs. Read the relevant one **before** starting work on a domain |

## Commands

All commands are run from the **repo root** (`commece-api-2604/`), not from `apps/commerce-api/`.

```bash
# Start required infrastructure (MySQL, Redis) before running the app
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

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when running locally.

## Project structure

This is a **multi-module Gradle project**.

- `apps/` — runnable Spring Boot applications (`commerce-api`, `commerce-batch`, `commerce-streamer`)
- `modules/` — reusable infrastructure configs (`jpa`, `redis`, `kafka`)
- `supports/` — add-on modules (`jackson`, `logging`, `monitoring`)

`apps` depend on `modules` and `supports`. `modules` and `supports` are not runnable (their `BootJar`
tasks are disabled). Main development happens in `apps/commerce-api`.

Package layout inside `commerce-api` and the rules that govern it are in the `architecture-rules` skill.

## Test setup

Tests run with `spring.profiles.active=test` and `user.timezone=Asia/Seoul` automatically (configured
in root `build.gradle.kts`). `maxParallelForks = 1`, so tests are sequential.

MySQL and Redis are started by **Testcontainers** (`MySqlTestContainersConfig`, `RedisTestContainersConfig`
in the `testFixtures` source set of `modules/jpa` and `modules/redis`). Only the Docker daemon needs to be
running — `docker-compose` is not required for tests.

Which annotation belongs to which kind of test:

| Test class type | Annotation |
|---|---|
| Domain object tests | plain JUnit (no Spring) |
| Domain service tests | `@ExtendWith(MockitoExtension.class)` |
| UseCase tests | `@SpringBootTest` |
| Controller slice tests | `@WebMvcTest` |
| E2E tests | `@SpringBootTest(webEnvironment = RANDOM_PORT)` |

Every integration/E2E test calls `databaseCleanUp.truncateAllTables()` in `@AfterEach`. `DatabaseCleanUp`
is a test fixture provided by `modules/jpa`, importable via `testFixtures(project(":modules:jpa"))`.

Fixture naming, assertion style, dependency injection in tests, and when a repository deserves a test
at all are covered in the `architecture-rules` skill.
