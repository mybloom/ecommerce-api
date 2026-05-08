package com.loopers.domain.point;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.loopers.domain.point.PointFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class PointTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("Point 생성 시, 초기값이 0으로 생성된다.")
        void createInitialPoint() {
            Point point = Point.createInitial(anInitialCommand());

            assertAll(
                    () -> assertThat(point.getMemberId()).isEqualTo(DEFAULT_MEMBER_ID),
                    () -> assertThat(point.getBalance().isZero()).isTrue()
            );
        }

        @DisplayName("memberId가 null이면 NPE 예외가 발생한다")
        @Test
        void throwsException_whenMemberIdIsNull() {

            assertThatThrownBy(() -> Point.createInitial(anInitialCommand(null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("charge")
    class Charge {

        @Test
        @DisplayName("양수 금액을 충전하면 잔액이 증가한다")
        void increasesBalance_whenAmountIsPositive() {
            Point point = anInitialPoint();

            point.charge(Money.of(1_000L));

            assertThat(point.getBalance()).isEqualTo(Money.of(1_000L));
        }

        @Test
        @DisplayName("0을 충전하면 BAD_REQUEST 예외가 발생한다")
        void throwsException_whenAmountIsZero() {
            Point point = anInitialPoint();

            assertThatThrownBy(() -> point.charge(Money.of(0L)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("음수를 충전하면 BAD_REQUEST 예외가 발생한다")
        void throwsException_whenAmountIsNegative() {
            Point point = anInitialPoint();

            assertThatThrownBy(() -> point.charge(Money.of(-1L)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("use")
    class Use {

        @Test
        @DisplayName("보유 잔액보다 적은 금액을 차감하면 잔액이 감소한다")
        void decreasesBalance_whenAmountIsLessThanBalance() {
            Point point = aPointWithBalance(1_000L);

            point.use(Money.of(300L));

            assertThat(point.getBalance()).isEqualTo(Money.of(700L));
        }

        @Test
        @DisplayName("보유 잔액과 같은 금액을 차감하면 잔액이 0이 된다")
        void becomesZero_whenAmountEqualsBalance() {
            Point point = aPointWithBalance(1_000L);

            point.use(Money.of(1_000L));

            assertThat(point.getBalance().isZero()).isTrue();
        }

        @Test
        @DisplayName("보유 잔액보다 큰 금액을 차감하면 CONFLICT 예외가 발생한다")
        void throwsException_whenAmountExceedsBalance() {
            Point point = aPointWithBalance(1_000L);

            assertThatThrownBy(() -> point.use(Money.of(2_000L)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("0을 차감하면 잔액이 변하지 않는다")
        void keepsBalance_whenAmountIsZero() {
            long initialBalance = 1_000L;
            Point point = aPointWithBalance(initialBalance);

            point.use(Money.of(0L));

            assertThat(point.getBalance()).isEqualTo(Money.of(initialBalance));
        }

        @Test
        @DisplayName("음수를 차감하면 예외가 발생한다")
        void throwsException_whenAmountIsNegative() {
            Point point = aPointWithBalance(1_000L);

            assertThatThrownBy(() -> point.use(Money.of(-1L)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
