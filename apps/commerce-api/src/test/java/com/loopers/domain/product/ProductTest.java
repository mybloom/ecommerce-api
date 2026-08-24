package com.loopers.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Nested
    @DisplayName("isVisibleToUser")
    class IsVisibleToUser {

        @Test
        @DisplayName("ON_SALE 상태이면 true를 반환한다")
        void returnsTrue_whenStatusIsOnSale() {
            Product product = ProductFixture.aProductWithStatus(ProductStatus.ON_SALE);

            assertThat(product.isVisibleToUser()).isTrue();
        }

        @Test
        @DisplayName("OFF_SALE 상태이면 false를 반환한다")
        void returnsFalse_whenStatusIsOffSale() {
            Product product = ProductFixture.aProductWithStatus(ProductStatus.OFF_SALE);

            assertThat(product.isVisibleToUser()).isFalse();
        }

        @Test
        @DisplayName("HIDDEN 상태이면 false를 반환한다")
        void returnsFalse_whenStatusIsHidden() {
            Product product = ProductFixture.aProductWithStatus(ProductStatus.HIDDEN);

            assertThat(product.isVisibleToUser()).isFalse();
        }
    }

    @Nested
    @DisplayName("isSoldOut")
    class IsSoldOut {

        @Test
        @DisplayName("재고가 0이면 true를 반환한다")
        void returnsTrue_whenStockIsZero() {
            Product product = ProductFixture.aProductWithStock(0);

            assertThat(product.isSoldOut()).isTrue();
        }

        @Test
        @DisplayName("재고가 1 이상이면 false를 반환한다")
        void returnsFalse_whenStockIsPositive() {
            Product product = ProductFixture.aProductWithStock(10);

            assertThat(product.isSoldOut()).isFalse();
        }
    }
}
