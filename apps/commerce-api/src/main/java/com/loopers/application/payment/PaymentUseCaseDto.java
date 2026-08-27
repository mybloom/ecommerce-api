package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentStatus;

import java.time.ZonedDateTime;

public class PaymentUseCaseDto {

    /**
     * 결제 금액은 입력에 없다. 주문의 totalAmount를 그대로 쓴다 (참고: 07_payment.md Payment-002).
     */
    public record PayInfo(Long memberId, String orderNumber, PaymentMethod method) {
    }

    /**
     * PG 거래 식별자와 내부 식별자 id는 담지 않는다 (참고: Payment-007, Order-011).
     */
    public record PayResult(
            String orderNumber,
            PaymentMethod method,
            PaymentStatus status,
            Long amount,
            ZonedDateTime approvedAt,
            String failureReason
    ) {
        public static PayResult from(String orderNumber, Payment payment) {
            return new PayResult(
                    orderNumber,
                    payment.getMethod(),
                    payment.getStatus(),
                    payment.getAmount().getAmount(),
                    payment.getApprovedAt(),
                    payment.getFailureReason()
            );
        }
    }
}
