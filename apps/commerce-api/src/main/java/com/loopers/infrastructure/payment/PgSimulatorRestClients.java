package com.loopers.infrastructure.payment;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 연결과 읽기 타임아웃을 <b>따로</b> 잡는다. 둘 다 SocketTimeoutException으로 오지만
 * 연결 실패는 요청이 나가지 못한 것이고 읽기 실패는 처리 여부를 모르는 것이라,
 * 어댑터가 이 둘을 구분해야 예외 번역이 성립한다 (참고: Payment-010).
 */
final class PgSimulatorRestClients {

    private PgSimulatorRestClients() {
    }

    static RestClient create(String baseUrl, int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
