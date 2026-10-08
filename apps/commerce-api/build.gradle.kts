plugins {
    id("info.solidsoft.pitest")
}

/**
 * 뮤테이션 테스트 — 테스트가 실제로 결함을 잡는지 도구가 직접 검증한다.
 *
 * `check` 에 붙이지 않는다. 별도 태스크로만 돌려서 평소 빌드와 커밋 시 테스트는 그대로 둔다.
 *   ./gradlew :apps:commerce-api:pitest
 */
pitest {
    junit5PluginVersion.set(project.properties["pitestJUnit5Version"] as String)
    // domain 만 대상으로 한다. 순수 단위 테스트 193개가 스프링 컨텍스트 없이 3초에 돈다
    targetClasses.set(setOf("com.loopers.domain.*"))
    // @SpringBootTest 가 딸려 들어가면 변이마다 테스트컨테이너가 뜬다. 반드시 못 박는다
    targetTests.set(setOf("com.loopers.domain.*"))
    // QueryDSL 이 생성하는 Q 클래스. 고칠 소스가 없다 — NullAway 의 excludedPaths 와 같은 이유
    excludedClasses.set(setOf("com.loopers.domain.*.Q*"))
    outputFormats.set(setOf("HTML"))
    timestampedReports.set(false)
    // 임계값(mutationThreshold)은 아직 걸지 않는다. 점수를 먼저 본다
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")
    implementation ("org.springframework.security:spring-security-crypto")

    // resilience — PG 요청 재시도. 데코레이터로 직접 조립하므로 스타터 없이 코어만 쓴다 (07_payment.md E.4)
    implementation("io.github.resilience4j:resilience4j-retry:${project.properties["resilience4jVersion"]}")
    // PG 서킷 브레이커. 재시도와 같은 방식으로 PgGatewayConfig 에서 조립한다 (07_payment.md F.4)
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:${project.properties["resilience4jVersion"]}")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
    // TestKindFilter 가 PostDiscoveryFilter 를 구현한다. 루트는 런타임에만 둔다
    testImplementation("org.junit.platform:junit-platform-launcher")
}

// 실제 pg-simulator(8082)를 부르는 테스트는 기본 스위트에서 뺀다.
// 외부 프로세스가 필요하고 PG가 40% 확률로 거절해 결과가 매번 달라진다.
tasks.test {
    useJUnitPlatform { excludeTags("external") }
}

// test 를 Docker 필요 여부로 둘로 나눈다. 분류 기준은 TestKindFilter 에 있다 — 테스트에 태그를 달지 않는다.
// 두 태스크를 합치면 test 와 같다.
//   ./gradlew :apps:commerce-api:unitTest         Docker 없이
//   ./gradlew :apps:commerce-api:integrationTest  Testcontainers
mapOf(
    "unitTest" to "순수 JUnit, MockitoExtension, @WebMvcTest. Docker 가 필요 없다",
    "integrationTest" to "@SpringBootTest. Testcontainers 로 MySQL 을 띄워 Docker 가 필요하다",
).forEach { (name, desc) ->
    tasks.register<Test>(name) {
        description = desc
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform { excludeTags("external") }
        // 루트 build.gradle.kts 가 test 에 거는 설정(프로파일, 타임존, 순차 실행)을 그대로 따른다
        val base = tasks.test.get()
        maxParallelForks = base.maxParallelForks
        systemProperties(base.systemProperties)
        jvmArgs = base.jvmArgs
        systemProperty("loopers.test.kind", name.removeSuffix("Test"))
    }
}

tasks.register<Test>("externalTest") {
    description = "pg-simulator 가 8082 에 떠 있어야 한다"
    group = "verification"
    useJUnitPlatform { includeTags("external") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
}
