package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상수 값은 PG가 정한 것이라 우리가 바꿀 수 없다. 다른 이름을 쓰면 승인 요청이 거절된다.
 */
@Getter
@RequiredArgsConstructor
public enum CardType {
    SAMSUNG("Samsung", "삼성카드", "삼성카드로 결제"),
    KB("KB", "국민카드", "국민카드로 결제"),
    HYUNDAI("Hyundai", "현대카드", "현대카드로 결제");

    private final String code;
    private final String label;
    private final String description;
}
