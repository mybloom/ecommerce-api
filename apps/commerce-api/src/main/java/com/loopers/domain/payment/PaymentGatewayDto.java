package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

public class PaymentGatewayDto {

    public record ApprovalCommand(
            Long memberId,
            String orderNumber,
            CardType cardType,
            String cardNo,
            Money amount
    ) {
    }

    /**
     * transactionKey는 <b>저장하지 않는다.</b> 승인된 뒤에만 존재해야 하는 값이라(참고: B.1 불변식)
     * 여기서는 로그로 흘려 대사 때 추적할 근거로만 쓴다.
     */
    public record Approval(String transactionKey, PgTransactionStatus status) {
    }
}
