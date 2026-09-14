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

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
