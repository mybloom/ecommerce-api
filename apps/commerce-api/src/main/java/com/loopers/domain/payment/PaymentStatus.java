package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PENDING("Pending", "승인대기", "결제가 접수되어 결과를 기다리는 상태"),
    APPROVED("Approved", "승인완료", "결제가 승인된 상태"),
    FAILED("Failed", "결제실패", "승인에 실패해 주문의 재고가 복원된 상태");

    private final String code;
    private final String label;
    private final String description;
}
