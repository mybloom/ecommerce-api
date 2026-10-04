package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(PgProperties.class)
@Configuration
public class PgGatewayConfig {

    /**
     * 어댑터는 PG 응답을 예외 타입으로 번역만 하고, 재시도는 바깥 데코레이터가 맡는다
     * (참고: 07_payment.md E.4 정한 것 2번). 서킷 브레이커를 넣게 되면 같은 자리에서 Retry 안쪽에 감싼다.
     */
    @Bean
    public PaymentGateway paymentGateway(PgProperties properties) {
        PaymentGateway adapter = new PgSimulatorPaymentGateway(
                PgSimulatorRestClients.create(
                        properties.baseUrl(),
                        properties.connectTimeoutMillis(),
                        properties.readTimeoutMillis()),
                properties.callbackUrl());
        return new RetryingPaymentGateway(adapter, properties.retry());
    }
}
