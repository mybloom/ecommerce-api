package com.loopers.domain.point;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class MoneyTest {

    @Nested
    @DisplayName("of() 생성 검증")
    class Create {

        @DisplayName("0 이상의 값으로 생성할 수 있다")
        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, Long.MAX_VALUE})
        void create_normal(long amount) {
            Money money = Money.of(amount);

            assertThat(money.getAmount()).isEqualTo(amount);
        }

        @Test
        @DisplayName("amount가 null이면 BAD_REQUEST 예외가 발생한다")
        void create_null() {
            assertThatThrownBy(() -> Money.of(null))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("amount가 음수면 BAD_REQUEST 예외가 발생한다")
        void create_negative() {
            assertThatThrownBy(() -> Money.of(-1L))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("ZERO 상수")
    class ZeroConstant {

        @Test
        @DisplayName("ZERO는 0원을 의미한다")
        void zero_is_zero() {
            assertAll(
                    () -> assertThat(Money.ZERO.getAmount()).isEqualTo(0L),
                    () -> assertThat(Money.ZERO.isZero()).isTrue()
            );
        }
    }

    @Nested
    @DisplayName("isZero()")
    class IsZero {

        @Test
        @DisplayName("금액이 0이면 true를 반환한다")
        void isZero_true() {
            assertAll(
                    () -> assertThat(Money.of(0L).isZero()).isTrue(),
                    () -> assertThat(Money.ZERO.isZero()).isTrue()
            );
        }

        @Test
        @DisplayName("금액이 0이 아니면 false를 반환한다")
        void isZero_false() {
            assertThat(Money.of(1L).isZero()).isFalse();
        }
    }

    @Nested
    @DisplayName("isPositive")
    class IsPositive {

        @Test
        @DisplayName("금액이 양수이면 true를 반환한다")
        void returnsTrue_whenAmountIsPositive() {
            assertThat(Money.of(1L).isPositive()).isTrue();
        }

        @Test
        @DisplayName("금액이 0이면 false를 반환한다")
        void returnsFalse_whenAmountIsZero() {
            assertThat(Money.of(0L).isPositive()).isFalse();
        }
    }

    @Nested
    @DisplayName("isLessThan")
    class IsLessThan {

        @Test
        @DisplayName("자신이 더 작으면 true를 반환한다")
        void returnsTrue_whenThisIsLess() {
            assertThat(Money.of(100L).isLessThan(Money.of(200L))).isTrue();
        }

        @Test
        @DisplayName("두 금액이 같으면 false를 반환한다")
        void returnsFalse_whenAmountsAreEqual() {
            assertThat(Money.of(100L).isLessThan(Money.of(100L))).isFalse();
        }

        @Test
        @DisplayName("자신이 더 크면 false를 반환한다")
        void returnsFalse_whenThisIsGreater() {
            assertThat(Money.of(200L).isLessThan(Money.of(100L))).isFalse();
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("두 금액을 더한 결과를 반환한다")
        void returnsSum() {
            Money result = Money.of(1_000L).add(Money.of(500L));

            assertThat(result).isEqualTo(Money.of(1_500L));
        }

        @Test
        @DisplayName("0을 더하면 자신과 같은 값을 반환한다")
        void returnsSameAmount_whenAddingZero() {
            Money result = Money.of(1_000L).add(Money.ZERO);

            assertThat(result).isEqualTo(Money.of(1_000L));
        }

        @Test
        @DisplayName("결과가 long 범위를 초과하면 ArithmeticException이 발생한다")
        void throwsException_whenResultOverflows() {
            Money max = Money.of(Long.MAX_VALUE);

            assertThatThrownBy(() -> max.add(Money.of(1L)))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    @Nested
    @DisplayName("subtract")
    class Subtract {

        @Test
        @DisplayName("두 금액을 뺀 결과를 반환한다")
        void returnsDifference() {
            Money result = Money.of(1_000L).subtract(Money.of(300L));

            assertThat(result).isEqualTo(Money.of(700L));
        }

        @Test
        @DisplayName("결과가 음수가 되면 BAD_REQUEST 예외가 발생한다")
        void throwsException_whenResultIsNegative() {
            assertThatThrownBy(() -> Money.of(100L).subtract(Money.of(200L)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("동등성")
    class Equality {

        @Test
        @DisplayName("같은 금액의 Money는 동등하고 hashCode도 같다")
        void equal_same_amount() {
            Money money1 = Money.of(1000L);
            Money money2 = Money.of(1000L);

            assertAll(
                    () -> assertThat(money1).isEqualTo(money2),
                    () -> assertThat(money1.hashCode()).isEqualTo(money2.hashCode())
            );
        }

        @Test
        @DisplayName("다른 금액의 Money는 동등하지 않다")
        void not_equal_different_amount() {
            Money money1 = Money.of(1000L);
            Money money2 = Money.of(2000L);

            assertThat(money1).isNotEqualTo(money2);
        }

        @Test
        @DisplayName("Money.ZERO와 Money.of(0L)은 동등하다")
        void zero_equals_of_zero() {
            assertThat(Money.ZERO).isEqualTo(Money.of(0L));
        }
    }
}
