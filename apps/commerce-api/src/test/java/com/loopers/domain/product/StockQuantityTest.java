package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockQuantityTest {

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("음수 값이 전달되면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenValueIsNegative() {
            assertThatThrownBy(() -> StockQuantity.of(-1))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("0은 유효한 재고 수량이다")
        void allowsZero() {
            assertThat(StockQuantity.of(0).getValue()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("isSoldOut")
    class IsSoldOut {

        @Test
        @DisplayName("재고가 0이면 true를 반환한다")
        void returnsTrue_whenValueIsZero() {
            assertThat(StockQuantity.of(0).isSoldOut()).isTrue();
        }

        @Test
        @DisplayName("재고가 1 이상이면 false를 반환한다")
        void returnsFalse_whenValueIsPositive() {
            assertThat(StockQuantity.of(5).isSoldOut()).isFalse();
        }
    }
}
