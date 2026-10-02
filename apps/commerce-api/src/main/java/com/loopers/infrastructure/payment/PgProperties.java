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
        int readTimeoutMillis
) {
}
