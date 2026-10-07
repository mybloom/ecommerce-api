package com.loopers.support;

import org.junit.jupiter.api.Tag;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * unitTest / integrationTest 태스크가 테스트를 나누는 기준. 테스트마다 태그를 달지 않고 어노테이션으로 가른다.
 *
 * <ul>
 *   <li>integration — 최상위 클래스에 {@link SpringBootTest}(메타 포함)나 {@code @Tag("integration")} 이 있다.
 *       전체 컨텍스트가 MySqlTestContainersConfig 를 불러와 Docker 가 필요하다</li>
 *   <li>unit — 나머지. 순수 JUnit, MockitoExtension, {@code @WebMvcTest} 슬라이스. Docker 없이 돈다</li>
 * </ul>
 *
 * <p>META-INF/services 로 JUnit Platform 에 자동 등록된다. 시스템 프로퍼티 {@code loopers.test.kind} 가 없으면
 * 아무것도 거르지 않으므로 {@code test} 태스크와 pitest 는 영향을 받지 않는다.
 */
public class TestKindFilter implements PostDiscoveryFilter {

    static final String PROPERTY = "loopers.test.kind";
    private static final String INTEGRATION = "integration";

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        String kind = System.getProperty(PROPERTY);
        if (kind == null) {
            return FilterResult.included("분류 없이 전체 실행");
        }
        Class<?> testClass = testClassOf(descriptor);
        if (testClass == null) {
            return FilterResult.included("클래스가 없는 노드(엔진)");
        }
        String actual = isIntegration(topLevel(testClass)) ? INTEGRATION : "unit";
        return FilterResult.includedIf(actual.equals(kind), () -> actual, () -> actual);
    }

    private static Class<?> testClassOf(TestDescriptor descriptor) {
        TestSource source = descriptor.getSource().orElse(null);
        if (source instanceof ClassSource classSource) {
            return classSource.getJavaClass();
        }
        if (source instanceof MethodSource methodSource) {
            return methodSource.getJavaClass();
        }
        return null;
    }

    // @Nested 는 바깥 클래스의 분류를 따른다
    private static Class<?> topLevel(Class<?> testClass) {
        Class<?> current = testClass;
        while (current.getEnclosingClass() != null) {
            current = current.getEnclosingClass();
        }
        return current;
    }

    private static boolean isIntegration(Class<?> testClass) {
        return AnnotationSupport.isAnnotated(testClass, SpringBootTest.class)
            || AnnotationSupport.findRepeatableAnnotations(testClass, Tag.class).stream()
                .anyMatch(tag -> tag.value().equals(INTEGRATION));
    }
}
