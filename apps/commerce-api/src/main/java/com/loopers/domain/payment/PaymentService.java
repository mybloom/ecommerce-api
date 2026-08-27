package com.loopers.domain.payment;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public Payment request(PaymentServiceDto.RequestCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public Payment approve(PaymentServiceDto.ApproveCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public Payment fail(PaymentServiceDto.FailCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }
}
