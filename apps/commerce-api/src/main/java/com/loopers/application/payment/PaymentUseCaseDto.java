package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;

import java.time.ZonedDateTime;
import org.jspecify.annotations.Nullable;

public class PaymentUseCaseDto {

    /**
     * API 계약이 도메인 enum 에 묶이지 않도록 레이어마다 따로 둔다.
     * 도메인에서 상수를 바꿔도 여기서 변환이 깨지며 드러난다.
     */
    public enum PaymentMethod {
        POINT
    }

    public enum PaymentStatus {
        PENDING, APPROVED, FAILED
    }

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
            @Nullable ZonedDateTime approvedAt,
            @Nullable String failureReason
    ) {
        public static PayResult from(String orderNumber, Payment payment) {
            return new PayResult(
                    orderNumber,
                    PaymentMethod.valueOf(payment.getMethod().name()),
                    PaymentStatus.valueOf(payment.getStatus().name()),
                    payment.getAmount().getAmount(),
                    payment.getApprovedAt(),
                    payment.getFailureReason()
            );
        }
    }
}
