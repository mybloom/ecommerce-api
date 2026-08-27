package com.loopers.domain.shared;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.*;

import static java.util.Objects.requireNonNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@EqualsAndHashCode
@Embeddable
public class Money {
    public static final Money ZERO = new Money(0L);

    private final Long amount;

    public static Money of(Long amount) {
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 빈 값이 될 수 없습니다.");
        }
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 음수가 될 수 없습니다.");
        }

        return new Money(amount);
    }

    public boolean isZero() {
        return amount == 0L;
    }

    public boolean isPositive() {
        return this.amount > 0;
    }

    public boolean isLessThan(Money other) {
        requireNonNull(other, "비교할 금액은 필수입니다.");
        return this.amount < other.amount;
    }

    public Money add(Money other) {
        requireNonNull(other, "더할 금액은 필수입니다.");
        return Money.of(Math.addExact(this.amount, other.amount));
    }

    public Money subtract(Money other) {
        requireNonNull(other, "뺄 금액은 필수입니다.");
        return Money.of(Math.subtractExact(this.amount, other.amount));
    }

    public Money multiply(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "곱할 수량은 1 이상이어야 합니다. quantity=" + quantity);
        }

        return Money.of(Math.multiplyExact(this.amount, (long) quantity));
    }
}
