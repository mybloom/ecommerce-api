package com.loopers.domain.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PENDING("Pending", "접수", "접수됨. 검증도 재고 확보도 아직인 상태"),
    AWAITING_PAYMENT("AwaitingPayment", "결제대기", "재고 확보 완료. 결제를 기다리는 상태"),
    ORDER_FAILED("OrderFailed", "주문실패", "접수 후 검증이나 재고 확보에 실패. 재고는 애초에 확보되지 않은 상태"),
    PAID("Paid", "결제완료", "결제가 완료된 상태"),
    PAYMENT_FAILED("PaymentFailed", "결제실패", "결제에 실패해 확보했던 재고가 복원된 상태");

    private final String code;
    private final String label;
    private final String description;
}
