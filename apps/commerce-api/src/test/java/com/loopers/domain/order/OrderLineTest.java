package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
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

class OrderLineTest {

    @Nested
    @DisplayName("of - 주문 라인 생성 시,")
    class Of {

        @Test
        @DisplayName("주문 시점의 상품 식별자·상품명·단가·수량을 담는다")
        void snapshotsProductNameAndUnitPrice() {
            // given
            Long productId = 7L;
            Product product = ProductFixture.aSavedProduct(productId);
            int orderQuantity = 2;

            // when
            OrderLine line = OrderLine.of(product, orderQuantity);

            // then
            assertAll(
                    () -> assertThat(line.getProductId()).isEqualTo(productId),
                    () -> assertThat(line.getProductName()).isEqualTo(ProductFixture.DEFAULT_NAME),
                    () -> assertThat(line.getUnitPrice()).isEqualTo(ProductFixture.DEFAULT_PRICE),
                    () -> assertThat(line.getQuantity()).isEqualTo(orderQuantity)
            );
        }

        @DisplayName("수량이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void throwsBadRequest_whenQuantityIsNotPositive(int invalidQuantity) {
            // given
            Product product = ProductFixture.aSavedProduct(7L);

            // when & then
            assertThatThrownBy(() -> OrderLine.of(product, invalidQuantity))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("lineAmount - 라인 금액 계산 시,")
    class LineAmount {

        @Test
        @DisplayName("단가 곱하기 수량을 반환한다")
        void returnsUnitPriceTimesQuantity() {
            // given
            Product product = ProductFixture.aSavedProduct(7L);
            int orderQuantity = 3;
            OrderLine line = OrderLine.of(product, orderQuantity);
            Money expectedAmount = Money.of(ProductFixture.DEFAULT_PRICE.getAmount() * orderQuantity);

            // when
            Money lineAmount = line.lineAmount();

            // then
            assertThat(lineAmount).isEqualTo(expectedAmount);
        }

        @Test
        @DisplayName("수량이 1이면 단가와 같다")
        void equalsUnitPrice_whenQuantityIsOne() {
            // given
            Product product = ProductFixture.aSavedProduct(7L);
            int orderQuantity = 1;
            OrderLine line = OrderLine.of(product, orderQuantity);

            // when
            Money lineAmount = line.lineAmount();

            // then
            assertThat(lineAmount).isEqualTo(ProductFixture.DEFAULT_PRICE);
        }
    }
}
