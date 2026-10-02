package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * PG가 알려주는 거래 상태. 우리 PaymentStatus와 별개다 — 저쪽 어휘를 그대로 받아두고,
 * 우리 결제를 어떻게 종결할지는 콜백 처리가 판단한다 (참고: UC-2).
 */
@Getter
@RequiredArgsConstructor
public enum PgTransactionStatus {
    PENDING("Pending", "처리중", "PG가 아직 승인 결과를 정하지 않은 상태"),
    SUCCESS("Success", "승인", "PG가 승인한 상태"),
    FAILED("Failed", "실패", "PG가 승인을 거절한 상태");

    private final String code;
    private final String label;
    private final String description;
}
