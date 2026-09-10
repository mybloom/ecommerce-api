package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;
import org.jspecify.annotations.Nullable;

public class PaymentServiceDto {

    public record RequestCommand(Long orderId, Long memberId, PaymentMethod method, Money amount) {
    }

    public record ApproveCommand(Long paymentId, @Nullable String transactionKey) {
    }

    public record FailCommand(Long paymentId, @Nullable String reason) {
    }
}
