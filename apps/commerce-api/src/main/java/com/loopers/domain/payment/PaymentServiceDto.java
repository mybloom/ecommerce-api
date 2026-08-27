package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

public class PaymentServiceDto {

    public record RequestCommand(Long orderId, Long memberId, PaymentMethod method, Money amount) {
    }

    public record ApproveCommand(Long paymentId, String transactionKey) {
    }

    public record FailCommand(Long paymentId, String reason) {
    }
}
