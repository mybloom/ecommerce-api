package com.loopers.application.payment;

import org.springframework.stereotype.Component;

@Component
public class PaymentUseCase {

    public PaymentUseCaseDto.PayResult pay(PaymentUseCaseDto.PayInfo info) {
        throw new UnsupportedOperationException("2단계에서 구현");
    }
}
