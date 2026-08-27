package com.loopers.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

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

    @Nested
    @DisplayName("decreaseStock")
    class DecreaseStock {

        @Test
        @DisplayName("요청 수량만큼 재고를 차감한다")
        void decreasesStock() {
            // given
            int initialStock = 10;
            int orderQuantity = 3;
            Product product = ProductFixture.aProductWithStock(initialStock);

            // when
            product.decreaseStock(orderQuantity);

            // then
            assertThat(product.getStockQuantity()).isEqualTo(StockQuantity.of(initialStock - orderQuantity));
        }

        @Test
        @DisplayName("재고 전량을 주문하면 재고가 0이 되고 품절 상태가 된다")
        void becomesSoldOut_whenAllStockIsOrdered() {
            // given
            int initialStock = 3;
            int orderQuantity = 3;
            Product product = ProductFixture.aProductWithStock(initialStock);

            // when
            product.decreaseStock(orderQuantity);

            // then
            assertAll(
                    () -> assertThat(product.getStockQuantity()).isEqualTo(StockQuantity.of(0)),
                    () -> assertThat(product.isSoldOut()).isTrue()
            );
        }

        @Test
        @DisplayName("재고보다 많은 수량을 주문하면 CONFLICT 예외가 발생한다")
        void throwsConflict_whenQuantityExceedsStock() {
            // given
            int initialStock = 2;
            int orderQuantity = 3;
            Product product = ProductFixture.aProductWithStock(initialStock);

            // when & then
            assertThatThrownBy(() -> product.decreaseStock(orderQuantity))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("품절 상품을 주문하면 CONFLICT 예외가 발생한다")
        void throwsConflict_whenProductIsSoldOut() {
            // given
            int soldOutStock = 0;
            int orderQuantity = 1;
            Product product = ProductFixture.aProductWithStock(soldOutStock);

            // when & then
            assertThatThrownBy(() -> product.decreaseStock(orderQuantity))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("재고가 부족해 실패하면 CONFLICT가 나고 재고는 그대로 남는다")
        void keepsStockUnchanged_whenDecreaseFails() {
            // given
            int initialStock = 2;
            int orderQuantity = 3;
            Product product = ProductFixture.aProductWithStock(initialStock);

            // when
            Throwable thrown = catchThrowable(() -> product.decreaseStock(orderQuantity));

            // then
            assertAll(
                    () -> assertThat(thrown).isInstanceOf(CoreException.class),
                    () -> assertThat(product.getStockQuantity()).isEqualTo(StockQuantity.of(initialStock))
            );
        }
    }

    @Nested
    @DisplayName("increaseStock")
    class IncreaseStock {

        @Test
        @DisplayName("요청 수량만큼 재고를 복원한다")
        void increasesStock() {
            // given
            int remainingStock = 7;
            int restoredQuantity = 3;
            Product product = ProductFixture.aProductWithStock(remainingStock);

            // when
            product.increaseStock(restoredQuantity);

            // then
            assertThat(product.getStockQuantity()).isEqualTo(StockQuantity.of(remainingStock + restoredQuantity));
        }

        @Test
        @DisplayName("품절 상품에 재고를 복원하면 품절이 풀린다")
        void becomesAvailable_whenSoldOutProductIsRestored() {
            // given
            int soldOutStock = 0;
            int restoredQuantity = 2;
            Product product = ProductFixture.aProductWithStock(soldOutStock);

            // when
            product.increaseStock(restoredQuantity);

            // then
            assertAll(
                    () -> assertThat(product.getStockQuantity()).isEqualTo(StockQuantity.of(restoredQuantity)),
                    () -> assertThat(product.isSoldOut()).isFalse()
            );
        }

        @Test
        @DisplayName("차감했던 수량을 그대로 복원하면 차감 전 재고로 돌아온다")
        void returnsToOriginalStock_whenDecreasedQuantityIsRestored() {
            // given
            int initialStock = 10;
            int orderQuantity = 4;
            Product product = ProductFixture.aProductWithStock(initialStock);
            product.decreaseStock(orderQuantity);

            // when
            product.increaseStock(orderQuantity);

            // then
            assertThat(product.getStockQuantity()).isEqualTo(StockQuantity.of(initialStock));
        }

        @Test
        @DisplayName("복원 수량이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // given
            int remainingStock = 5;
            int invalidQuantity = 0;
            Product product = ProductFixture.aProductWithStock(remainingStock);

            // when & then
            assertThatThrownBy(() -> product.increaseStock(invalidQuantity))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
