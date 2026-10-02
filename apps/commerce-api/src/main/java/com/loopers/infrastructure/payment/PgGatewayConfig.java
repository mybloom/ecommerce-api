package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties(PgProperties.class)
@Configuration
public class PgGatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(PgProperties properties) {
        return new PgSimulatorPaymentGateway(
                PgSimulatorRestClients.create(
                        properties.baseUrl(),
                        properties.connectTimeoutMillis(),
                        properties.readTimeoutMillis()),
                properties.callbackUrl());
    }
}
