package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentUseCaseDto;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    /**
     * 결제 금액은 요청에 포함하지 않는다 (참고: 07_payment.md Payment-002).
     */
    public record PayRequest(
            @NotBlank(message = "주문번호는 필수입니다.")
            String orderNumber,

            @NotNull(message = "결제 수단은 필수입니다.")
            PaymentMethod method
    ) {
        public PaymentUseCaseDto.PayInfo toInfo(Long memberId) {
            return new PaymentUseCaseDto.PayInfo(memberId, orderNumber, method);
        }
    }

    public record PayResponse(
            String orderNumber,
            PaymentMethod method,
            PaymentStatus status,
            Long amount,
            ZonedDateTime approvedAt,
            String failureReason
    ) {
        public static PayResponse from(PaymentUseCaseDto.PayResult result) {
            return new PayResponse(
                    result.orderNumber(),
                    result.method(),
                    result.status(),
                    result.amount(),
                    result.approvedAt(),
                    result.failureReason()
            );
        }
    }
}
