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

    @Nested
    @DisplayName("increaseLikeCount")
    class IncreaseLikeCount {

        @Test
        @DisplayName("호출하면 좋아요 수가 1 증가한다")
        void increasesLikeCountByOne_whenCalled() {
            Product product = ProductFixture.aProduct();

            product.increaseLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("여러 번 호출하면 호출 횟수만큼 누적된다")
        void accumulatesLikeCount_whenCalledMultipleTimes() {
            Product product = ProductFixture.aProduct();
            int callCount = 3;

            for (int i = 0; i < callCount; i++) {
                product.increaseLikeCount();
            }

            assertThat(product.getLikeCount()).isEqualTo(callCount);
        }
    }

    @Nested
    @DisplayName("decreaseLikeCount")
    class DecreaseLikeCount {

        @Test
        @DisplayName("호출하면 좋아요 수가 1 감소한다")
        void decreasesLikeCountByOne_whenCalled() {
            int initialLikeCount = 5;
            Product product = ProductFixture.aProductForBrandWithLikeCount(
                    ProductFixture.DEFAULT_BRAND_ID, initialLikeCount);

            product.decreaseLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(initialLikeCount - 1);
        }

        @Test
        @DisplayName("좋아요 수가 0이면 감소시키지 않는다")
        void keepsZero_whenLikeCountIsAlreadyZero() {
            Product product = ProductFixture.aProduct();

            product.decreaseLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(0);
        }
    }
}
