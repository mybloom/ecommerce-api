package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * callbackUrl은 <b>PG가 우리를 다시 부르는 주소</b>라 우리 외부 주소여야 한다.
 * 시뮬레이터는 이 값이 http://localhost:8080 으로 시작하지 않으면 요청을 거절한다.
 */
@ConfigurationProperties(prefix = "pg")
public record PgProperties(
        String baseUrl,
        String callbackUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        RetryPolicy retry,
        CircuitBreakerPolicy circuitBreaker
) {

    /**
     * 재시도의 숫자만 담는다. 대기는 무작위로 고르는 범위이고, Retry-After가 있으면 그 값을 따른다.
     * Retry-After가 retryAfterCapMillis 를 넘으면 기다리지 않고 포기한다 (참고: 07_payment.md E.3).
     */
    public record RetryPolicy(
            int maxAttempts,
            long connectionFailedMaxWaitMillis,
            long rateLimitedMinWaitMillis,
            long rateLimitedMaxWaitMillis,
            long unavailableMinWaitMillis,
            long unavailableMaxWaitMillis,
            long retryAfterCapMillis
    ) {
    }

    /**
     * 서킷 브레이커의 숫자만 담는다. 최근 slidingWindowSize 건(호출 수 기준) 중 실패율이 failureRateThreshold(%) 이상이면
     * 열리고, waitDurationInOpenStateMillis 뒤 permittedNumberOfCallsInHalfOpenState 건으로 시험한다
     * (참고: 07_payment.md F.3).
     */
    public record CircuitBreakerPolicy(
            int slidingWindowSize,
            int minimumNumberOfCalls,
            float failureRateThreshold,
            long waitDurationInOpenStateMillis,
            int permittedNumberOfCallsInHalfOpenState
    ) {
    }
}
