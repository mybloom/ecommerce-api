package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentUseCaseDto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import org.jspecify.annotations.Nullable;

public class PaymentV1Dto {

    /**
     * 결제 금액은 요청에 포함하지 않는다 (참고: 07_payment.md Payment-002).
     */
    public record PayRequest(
            @NotBlank(message = "주문번호는 필수입니다.")
            String orderNumber,

            @NotNull(message = "결제 수단은 필수입니다.")
            PaymentUseCaseDto.PaymentMethod method,

            PaymentUseCaseDto.@Nullable CardType cardType,

            @Nullable String cardNo
    ) {
        /**
         * 카드 정보는 수단이 CARD일 때만 필수라 필드 단위 어노테이션으로 표현할 수 없다.
         * <p>
         * 카드 번호의 형식은 검증하지 않는다 — 수단별 검증 규칙은 아직 보류다 (참고: C.2).
         */
        @AssertTrue(message = "카드 결제는 카드 종류와 카드 번호가 필수입니다.")
        public boolean isCardInfoPresent() {
            if (method != PaymentUseCaseDto.PaymentMethod.CARD) {
                return true;
            }

            return cardType != null && cardNo != null && !cardNo.isBlank();
        }

        public PaymentUseCaseDto.PayInfo toInfo(Long memberId) {
            return new PaymentUseCaseDto.PayInfo(memberId, orderNumber, method, cardType, cardNo);
        }
    }

    /**
     * PG가 보내오는 승인 결과. <b>필드 이름은 PG가 정한 것을 따른다</b> —
     * 명세가 orderNumber라 부르는 값이 여기서는 orderId다 (참고: UC-2).
     */
    public record PgCallbackRequest(
            @NotBlank(message = "거래 식별자는 필수입니다.")
            String transactionKey,

            @NotBlank(message = "주문번호는 필수입니다.")
            String orderId,

            @NotNull(message = "PG 거래 상태는 필수입니다.")
            PaymentUseCaseDto.PgTransactionStatus status,

            @NotNull(message = "승인 금액은 필수입니다.")
            Long amount,

            @Nullable String reason
    ) {
        public PaymentUseCaseDto.PgCallbackInfo toInfo() {
            return new PaymentUseCaseDto.PgCallbackInfo(transactionKey, orderId, status, amount, reason);
        }
    }

    public record PayResponse(
            String orderNumber,
            PaymentUseCaseDto.PaymentMethod method,
            PaymentUseCaseDto.PaymentStatus status,
            Long amount,
            @Nullable ZonedDateTime approvedAt,
            @Nullable String failureReason
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
