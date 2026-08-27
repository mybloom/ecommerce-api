package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이 열거형이 나누는 것은 "무엇으로 내느냐"가 아니라 결과가 언제 확정되느냐다 (참고: 07_payment.md Payment-003).
 * CARD는 콜백(UC-2)과 함께 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    POINT("Point", "포인트", "보유 포인트로 결제. 요청 트랜잭션 안에서 확정된다");

    private final String code;
    private final String label;
    private final String description;
}
