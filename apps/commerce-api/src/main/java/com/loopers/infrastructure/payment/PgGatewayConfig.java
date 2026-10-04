package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(PgProperties.class)
@Configuration
public class PgGatewayConfig {

    /**
     * 어댑터는 PG 응답을 예외 타입으로 번역만 하고, 재시도와 서킷은 데코레이터가 맡는다
     * (참고: 07_payment.md E.4 정한 것 2번, F.4).
     * <p>
     * 서킷이 재시도 안쪽에 있어 시도 하나하나가 따로 기록된다. 재시도 대기 중에 회로가 열리면 다음 시도는
     * 서킷이 열린 실패를 받고, 그것은 재시도 대상이 아니라 거기서 멈춘다.
     */
    @Bean
    public PaymentGateway paymentGateway(PgProperties properties) {
        PaymentGateway adapter = new PgSimulatorPaymentGateway(
                PgSimulatorRestClients.create(
                        properties.baseUrl(),
                        properties.connectTimeoutMillis(),
                        properties.readTimeoutMillis()),
                properties.callbackUrl());
        return new RetryingPaymentGateway(
                new CircuitBreakingPaymentGateway(adapter, properties.circuitBreaker()),
                properties.retry());
    }
}
