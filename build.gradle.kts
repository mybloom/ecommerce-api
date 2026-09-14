import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Project.DEFAULT_VERSION
import org.springframework.boot.gradle.tasks.bundling.BootJar

/** --- configuration functions --- */
fun getGitHash(): String {
    return runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
    }.getOrElse { "init" }
}

/** --- project configurations --- */
plugins {
    java
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("net.ltgt.errorprone") version "4.1.0" apply false
    // 뮤테이션 테스트. subprojects 에서 일괄 적용하지 않고 apps:commerce-api 에만 건다
    id("info.solidsoft.pitest") version "1.15.0" apply false
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allprojects {
    val projectGroup: String by project
    group = projectGroup
    version = if (version == DEFAULT_VERSION) getGitHash() else version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")
    apply(plugin = "net.ltgt.errorprone")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${project.properties["springCloudDependenciesVersion"]}")
            mavenBom("org.testcontainers:testcontainers-bom:${project.properties["testcontainersVersion"]}")
        }
    }

    dependencies {
        // Web
        runtimeOnly("org.springframework.boot:spring-boot-starter-validation")
        // Spring
        implementation("org.springframework.boot:spring-boot-starter")
        // Serialize
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        // Lombok
        implementation("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        // Null 안전성 (docs/decisions/0001)
        implementation("org.jspecify:jspecify:${project.properties["jspecifyVersion"]}")
        "errorprone"("com.google.errorprone:error_prone_core:${project.properties["errorProneVersion"]}")
        "errorprone"("com.uber.nullaway:nullaway:${project.properties["nullAwayVersion"]}")
        // Test
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        // testcontainers:mysql 이 jdbc 사용함
        testRuntimeOnly("com.mysql:mysql-connector-j")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("com.ninja-squad:springmockk:${project.properties["springMockkVersion"]}")
        testImplementation("org.mockito:mockito-core:${project.properties["mockitoVersion"]}")
        testImplementation("org.instancio:instancio-junit:${project.properties["instancioJUnitVersion"]}")
        // Testcontainers
        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:testcontainers")
        testImplementation("org.testcontainers:junit-jupiter")
    }

    tasks.withType(Jar::class) { enabled = true }
    tasks.withType(BootJar::class) { enabled = false }

    configure(allprojects.filter { it.parent?.name.equals("apps") }) {
        tasks.withType(Jar::class) { enabled = false }
        tasks.withType(BootJar::class) { enabled = true }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            // NullAway 만 켠다. Error Prone 의 나머지 검사는 이번 결정의 범위가 아니다.
            disableAllChecks = true
            check("NullAway", CheckSeverity.ERROR)
            // 생성 소스(QueryDSL Q 클래스)는 고칠 소스가 없고,
            // 테스트는 일부러 null 을 넣거나 응답의 optional 필드를 바로 꺼내 쓰는 자리라 제외한다.
            excludedPaths = ".*/build/generated/.*|.*/src/test/.*|.*/src/testFixtures/.*"
            option("NullAway:AnnotatedPackages", "com.loopers")
            // 프레임워크가 리플렉션으로 채우는 필드 - 생성자에서 초기화되지 않아도 정상이다
            option(
                "NullAway:ExcludedFieldAnnotations",
                "jakarta.persistence.Id," +
                    "jakarta.persistence.Column," +
                    "jakarta.persistence.Embedded," +
                    "jakarta.persistence.Enumerated," +
                    "jakarta.persistence.OneToMany," +
                    "jakarta.persistence.GeneratedValue," +
                    "jakarta.persistence.PersistenceContext," +
                    "org.springframework.beans.factory.annotation.Autowired," +
                    "org.springframework.beans.factory.annotation.Value," +
                    "org.springframework.test.context.bean.override.mockito.MockitoBean," +
                    "org.springframework.test.context.bean.override.mockito.MockitoSpyBean," +
                    "org.mockito.Mock",
            )
        }
    }

    tasks.test {
        maxParallelForks = 1
        useJUnitPlatform()
        systemProperty("user.timezone", "Asia/Seoul")
        systemProperty("spring.profiles.active", "test")
        jvmArgs("-Xshare:off")
    }

    tasks.withType<JacocoReport> {
        mustRunAfter("test")
        executionData(fileTree(layout.buildDirectory.asFile).include("jacoco/*.exec"))
        reports {
            xml.required = true
            csv.required = false
            html.required = false
        }
        afterEvaluate {
            classDirectories.setFrom(
                files(
                    classDirectories.files.map {
                        fileTree(it)
                    },
                ),
            )
        }
    }
}

// module-container 는 task 를 실행하지 않도록 한다.
project("apps") { tasks.configureEach { enabled = false } }
project("modules") { tasks.configureEach { enabled = false } }
project("supports") { tasks.configureEach { enabled = false } }
