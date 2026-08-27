package com.loopers.domain.product;

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

    @Nested
    @DisplayName("isEnough")
    class IsEnough {

        @DisplayName("재고가 요청 수량 이상이면 true를 반환한다")
        @ParameterizedTest
        @ValueSource(ints = {3, 10})
        void returnsTrue_whenStockIsEnough(int stock) {
            // given
            StockQuantity stockQuantity = StockQuantity.of(stock);
            int orderQuantity = 3;

            // when & then
            assertThat(stockQuantity.isEnough(orderQuantity)).isTrue();
        }

        @Test
        @DisplayName("재고가 요청 수량보다 적으면 false를 반환한다")
        void returnsFalse_whenStockIsNotEnough() {
            // given
            StockQuantity stockQuantity = StockQuantity.of(2);
            int orderQuantity = 3;

            // when & then
            assertThat(stockQuantity.isEnough(orderQuantity)).isFalse();
        }
    }

    @Nested
    @DisplayName("subtract")
    class Subtract {

        @Test
        @DisplayName("요청 수량만큼 차감한 재고를 반환한다")
        void returnsReducedStock() {
            // given
            StockQuantity stockQuantity = StockQuantity.of(10);
            int orderQuantity = 3;

            // when
            StockQuantity result = stockQuantity.subtract(orderQuantity);

            // then
            assertThat(result).isEqualTo(StockQuantity.of(7));
        }

        @Test
        @DisplayName("재고 전량을 차감하면 0이 되고 품절 상태가 된다")
        void becomesSoldOut_whenAllStockIsSubtracted() {
            // given
            StockQuantity stockQuantity = StockQuantity.of(3);
            int orderQuantity = 3;

            // when
            StockQuantity result = stockQuantity.subtract(orderQuantity);

            // then
            assertAll(
                    () -> assertThat(result).isEqualTo(StockQuantity.of(0)),
                    () -> assertThat(result.isSoldOut()).isTrue()
            );
        }

        @Test
        @DisplayName("재고보다 많은 수량을 차감하면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenQuantityExceedsStock() {
            // given
            StockQuantity stockQuantity = StockQuantity.of(2);
            int orderQuantity = 3;

            // when & then
            assertThatThrownBy(() -> stockQuantity.subtract(orderQuantity))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("차감 수량이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void throwsBadRequest_whenQuantityIsNotPositive(int invalidQuantity) {
            // given
            StockQuantity stockQuantity = StockQuantity.of(10);

            // when & then
            assertThatThrownBy(() -> stockQuantity.subtract(invalidQuantity))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("요청 수량만큼 증가한 재고를 반환한다")
        void returnsIncreasedStock() {
            // given
            StockQuantity stockQuantity = StockQuantity.of(10);
            int restoreQuantity = 3;

            // when
            StockQuantity result = stockQuantity.add(restoreQuantity);

            // then
            assertThat(result).isEqualTo(StockQuantity.of(13));
        }

        @DisplayName("증가 수량이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void throwsBadRequest_whenQuantityIsNotPositive(int invalidQuantity) {
            // given
            StockQuantity stockQuantity = StockQuantity.of(10);

            // when & then
            assertThatThrownBy(() -> stockQuantity.add(invalidQuantity))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
