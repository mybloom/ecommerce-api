package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

import java.time.ZonedDateTime;

public class Payment {

    public Long getId() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public Long getOrderId() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public PaymentMethod getMethod() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public PaymentStatus getStatus() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public Money getAmount() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public String getTransactionKey() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public ZonedDateTime getApprovedAt() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public String getFailureReason() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }
}
