# CLAUDE.md

이 문서는 Claude Code(claude.ai/code)가 이 리포지토리에서 작업할 때 참고하는 안내서다.

**어떻게 실행하는지**와 **리포지토리가 어떻게 구성되어 있는지**를 다룬다. 코딩 규칙과 개발 절차는
skill에 따로 있다(아래 참조). 같은 내용이 두 곳에 있으면 서로 어긋나기 시작하므로 이 경계를 유지한다.

## 규칙과 작업 절차는 skill에 있다

| 위치 | 내용 |
|---|---|
| `.claude/skills/architecture-rules/` | 레이어 의존성, 패키지 네이밍, DTO 변환 체인, 예외 처리(`CoreException` / `ErrorType`), Lombok 정책, 테스트 컨벤션, 참고 구현체 |
| `.claude/skills/architecture-rules/references/` | 위 규칙의 **레이어별 상세와 예제**. 필요할 때만 연다 (`domain-modeling`, `domain-service`, `usecase`, `controller`, `testing`, `entity-and-vo-testing`) |
| `.claude/skills/tfd-workflow/` | 새 기능 추가 절차 — 한 번에 한 레이어씩, 테스트 먼저, 레이어마다 멈춰서 확인. **안에서 밖으로**(도메인부터) |
| `.claude/skills/tfd-workflow-outside-in/` | 같은 절차를 **밖에서 안으로**(API 진입점부터). 아래 레이어는 계약만 만들어 스켈레톤으로 막고 한 겹씩 채운다 |
| `.claude/skills/test-effectiveness/` | 작성한 테스트가 실제로 결함을 잡는지 검증. **3단계 종료 직후 도메인은 `pitest` 로**, 5단계 직후 나머지는 손으로 |
| `.claude/skills/git-conventions/` | 커밋 메시지 형식, 커밋 분리 기준, 브랜치별 문서 소유, push/PR 규칙 |
| `.claude/skills/pr-history/` | 기능을 마친 뒤 `featureN`과 `claude_docs`에 히스토리를 다시 구성 |
| `docs/도메인모델/` | 도메인 명세. 해당 도메인 작업을 **시작하기 전에** 먼저 읽는다 |

## 명령어

모든 명령어는 `apps/commerce-api/`가 아니라 **리포지토리 루트**(`commece-api-2604/`)에서 실행한다.

```bash
# 애플리케이션 실행에 필요한 인프라 기동 (MySQL, Redis)
docker-compose -f ./docker/infra-compose.yml up -d

# 전체 프로젝트 빌드
./gradlew build

# 이 모듈의 테스트만 실행
./gradlew :apps:commerce-api:test

# 테스트 클래스 하나만 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.point.PointServiceTest"

# 테스트 메서드 하나만 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.point.PointServiceTest.Register.saveMember_whenCreateSucceeds"

# 도메인 뮤테이션 테스트 (Docker 불필요, 20초)
./gradlew :apps:commerce-api:pitest

# 애플리케이션 실행 (local 프로파일이 기본)
./gradlew :apps:commerce-api:bootRun
```

로컬 실행 중에는 Swagger UI를 `http://localhost:8080/swagger-ui.html`에서 볼 수 있다.

## 프로젝트 구조

**멀티모듈 Gradle 프로젝트**다.

- `apps/` — 실행 가능한 Spring Boot 애플리케이션 (`commerce-api`, `commerce-batch`, `commerce-streamer`)
- `modules/` — 재사용 가능한 인프라 설정 (`jpa`, `redis`, `kafka`)
- `supports/` — 부가 모듈 (`jackson`, `logging`, `monitoring`)

`apps`가 `modules`와 `supports`에 의존한다. `modules`와 `supports`는 실행 대상이 아니다(`BootJar`
태스크가 비활성화되어 있다). 주 개발은 `apps/commerce-api`에서 이루어진다.

`commerce-api` 내부의 패키지 구조와 그것을 지배하는 규칙은 `architecture-rules` skill에 있다.

## 테스트 환경

테스트는 `spring.profiles.active=test`와 `user.timezone=Asia/Seoul`이 자동으로 적용된 상태로 실행된다
(루트 `build.gradle.kts`에 설정되어 있다). `maxParallelForks = 1`이라 테스트는 순차적으로 돈다.

MySQL과 Redis는 **Testcontainers**가 직접 띄운다(`modules/jpa`, `modules/redis`의 `testFixtures`
소스셋에 있는 `MySqlTestContainersConfig`, `RedisTestContainersConfig`). Docker 데몬만 실행 중이면
되고, 테스트에 `docker-compose`는 필요하지 않다.

테스트 종류별로 쓰는 애노테이션:

| 테스트 종류 | 애노테이션 |
|---|---|
| 도메인 객체 테스트 | 순수 JUnit (Spring 없음) |
| 도메인 서비스 테스트 | `@ExtendWith(MockitoExtension.class)` |
| UseCase 테스트 | `@SpringBootTest` |
| 컨트롤러 슬라이스 테스트 | `@WebMvcTest` |
| E2E 테스트 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` |

모든 통합/E2E 테스트는 `@AfterEach`에서 `databaseCleanUp.truncateAllTables()`를 호출한다.
`DatabaseCleanUp`은 `modules/jpa`가 제공하는 테스트 픽스처이며,
`testFixtures(project(":modules:jpa"))`로 가져다 쓴다.

픽스처 명명, 단언 스타일, 테스트에서의 의존성 주입, 리포지토리에 테스트를 작성할지 판단하는 기준은
`architecture-rules` skill에 있다.
