package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.util.Objects.requireNonNull;

@Getter
@Entity
@Table(name = "point")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Point {
    private static final long INITIAL_POINT_AMOUNT = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Money balance;

    @Column(nullable = false, unique = true)
    private Long memberId;

    public static Point createInitial(PointServiceDto.CreateInitialCommand command) {
        Point point = new Point();

        point.balance = Money.of(INITIAL_POINT_AMOUNT);
        point.memberId = requireNonNull(command.memberId());

        return point;
    }

    public void charge(Money amount) {
        if (!amount.isPositive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "충전 금액은 1 이상이어야 합니다. amount=" + amount);
        }

        this.balance = this.balance.add(amount);
    }

    public void use(Money amount) {
        if (this.balance.isLessThan(amount)) {
            throw new CoreException(ErrorType.CONFLICT,
                    "잔액이 부족합니다. balance=" + this.balance + ", amount=" + amount);
        }

        this.balance = this.balance.subtract(amount);
    }
}
