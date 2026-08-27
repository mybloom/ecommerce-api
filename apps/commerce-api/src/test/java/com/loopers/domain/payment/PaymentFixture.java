package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

public class PaymentFixture {

    public static final Long DEFAULT_ORDER_ID = 11L;
    public static final Long DEFAULT_MEMBER_ID = 1L;
    public static final Money DEFAULT_AMOUNT = Money.of(200_000L);
    public static final String DEFAULT_FAILURE_REASON = "잔액이 부족합니다.";

    public static Payment aRequestedPayment() {
        return Payment.request(DEFAULT_ORDER_ID, DEFAULT_MEMBER_ID, PaymentMethod.POINT, DEFAULT_AMOUNT);
    }

    public static Payment aRequestedPaymentOf(Long memberId) {
        return Payment.request(DEFAULT_ORDER_ID, memberId, PaymentMethod.POINT, DEFAULT_AMOUNT);
    }

    public static Payment anApprovedPayment() {
        Payment payment = aRequestedPayment();

        payment.approve(null);
        return payment;
    }

    public static Payment aFailedPayment() {
        Payment payment = aRequestedPayment();

        payment.fail(DEFAULT_FAILURE_REASON);
        return payment;
    }
}
