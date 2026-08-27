package com.loopers.domain.order;

import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    @Nested
    @DisplayName("draft - 주문 접수 시,")
    class Draft {

        @Test
        @DisplayName("접수된 주문은 PENDING 상태로 회원·주문번호·키를 담지만 라인도 금액도 없는 상태다")
        void createsPendingOrderWithoutLinesAndAmount() {
            // given
            Long memberId = OrderFixture.DEFAULT_MEMBER_ID;
            OrderNumber orderNumber = OrderFixture.DEFAULT_ORDER_NUMBER;
            IdempotencyKey idempotencyKey = OrderFixture.DEFAULT_IDEMPOTENCY_KEY;

            // when
            Order order = Order.draft(memberId, orderNumber, idempotencyKey);

            // then
            assertAll(
                    () -> assertThat(order.getMemberId()).isEqualTo(memberId),
                    () -> assertThat(order.getOrderNumber()).isEqualTo(orderNumber),
                    () -> assertThat(order.getIdempotencyKey()).isEqualTo(idempotencyKey),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(Money.ZERO),
                    () -> assertThat(order.getLines()).isEmpty(),
                    () -> assertThat(order.getOrderedAt()).isNotNull()
            );
        }
    }

    @Nested
    @DisplayName("confirm - 주문 확정 시,")
    class Confirm {

        @Test
        @DisplayName("라인을 붙이고 총액을 확정한 뒤 결제대기로 전이한다")
        void confirmsLinesAndTransitionsToAwaitingPayment() {
            // given
            Order order = OrderFixture.aDraftedOrder();
            List<OrderLine> lines = OrderFixture.aLines();
            Money expectedTotal = Money.of(
                    ProductFixture.DEFAULT_PRICE.getAmount() * OrderFixture.DEFAULT_QUANTITY);

            // when
            order.confirm(lines);

            // then
            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(expectedTotal),
                    () -> assertThat(order.getLines()).hasSize(1)
            );
        }

        @Test
        @DisplayName("총액은 모든 라인 금액의 합과 같다")
        void totalAmountEqualsSumOfLineAmounts() {
            // given
            Order order = OrderFixture.aDraftedOrder();
            int firstQuantity = 2;
            int secondQuantity = 3;
            List<OrderLine> lines = List.of(
                    OrderLine.of(ProductFixture.aSavedProduct(1L), firstQuantity),
                    OrderLine.of(ProductFixture.aSavedProduct(2L), secondQuantity));
            Money expectedTotal = Money.of(
                    ProductFixture.DEFAULT_PRICE.getAmount() * (firstQuantity + secondQuantity));

            // when
            order.confirm(lines);

            // then
            assertThat(order.getTotalAmount()).isEqualTo(expectedTotal);
        }

        @Test
        @DisplayName("이미 확정된 주문을 다시 확정하면 CONFLICT 예외가 발생한다")
        void throwsConflict_whenOrderIsAlreadyConfirmed() {
            // given
            Order order = OrderFixture.aConfirmedOrder();
            List<OrderLine> lines = OrderFixture.aLines();

            // when & then
            assertThatThrownBy(() -> order.confirm(lines))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("실패로 끝난 주문은 확정할 수 없고 CONFLICT 예외가 발생한다")
        void throwsConflict_whenOrderAlreadyFailed() {
            // given
            Order order = OrderFixture.aFailedOrder();
            List<OrderLine> lines = OrderFixture.aLines();

            // when & then
            assertThatThrownBy(() -> order.confirm(lines))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("라인이 비어 있으면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenLinesAreEmpty() {
            // given
            Order order = OrderFixture.aDraftedOrder();
            List<OrderLine> emptyLines = List.of();

            // when & then
            assertThatThrownBy(() -> order.confirm(emptyLines))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("같은 상품이 두 라인으로 들어오면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenProductIdIsDuplicated() {
            // given
            Order order = OrderFixture.aDraftedOrder();
            Long duplicatedProductId = 7L;
            List<OrderLine> duplicatedLines = List.of(
                    OrderLine.of(ProductFixture.aSavedProduct(duplicatedProductId), 1),
                    OrderLine.of(ProductFixture.aSavedProduct(duplicatedProductId), 2));

            // when & then
            assertThatThrownBy(() -> order.confirm(duplicatedLines))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("확정에 실패하면 CoreException이 나고 주문은 접수 상태에 총액 0으로 남는다")
        void staysPending_whenConfirmFails() {
            // given
            Order order = OrderFixture.aDraftedOrder();
            List<OrderLine> emptyLines = List.of();

            // when
            Throwable thrown = catchThrowable(() -> order.confirm(emptyLines));

            // then
            assertAll(
                    () -> assertThat(thrown).isInstanceOf(CoreException.class),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(Money.ZERO),
                    () -> assertThat(order.getLines()).isEmpty()
            );
        }
    }

    @Nested
    @DisplayName("markOrderFailed - 확정 실패 처리 시,")
    class MarkOrderFailed {

        @Test
        @DisplayName("접수 상태의 주문을 주문실패로 전이하고 라인과 총액은 비어 있는 채로 둔다")
        void transitionsToOrderFailed() {
            // given
            Order order = OrderFixture.aDraftedOrder();

            // when
            order.markOrderFailed();

            // then
            assertAll(
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDER_FAILED),
                    () -> assertThat(order.getLines()).isEmpty(),
                    () -> assertThat(order.getTotalAmount()).isEqualTo(Money.ZERO)
            );
        }

        @Test
        @DisplayName("이미 확정된 주문은 실패 처리할 수 없고 CONFLICT 예외가 발생한다")
        void throwsConflict_whenOrderIsAlreadyConfirmed() {
            // given
            Order order = OrderFixture.aConfirmedOrder();

            // when & then
            assertThatThrownBy(order::markOrderFailed)
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("isOwnedBy - 소유권 확인 시,")
    class IsOwnedBy {

        @Test
        @DisplayName("주문한 회원이면 true를 반환한다")
        void returnsTrue_whenMemberIsOwner() {
            // given
            Long ownerId = 42L;
            Order order = OrderFixture.aDraftedOrderOf(ownerId);

            // when & then
            assertThat(order.isOwnedBy(ownerId)).isTrue();
        }

        @Test
        @DisplayName("다른 회원이면 false를 반환한다")
        void returnsFalse_whenMemberIsNotOwner() {
            // given
            Long ownerId = 42L;
            Long otherMemberId = 99L;
            Order order = OrderFixture.aDraftedOrderOf(ownerId);

            // when & then
            assertThat(order.isOwnedBy(otherMemberId)).isFalse();
        }
    }

    @Nested
    @DisplayName("isPayable - 결제 가능 여부 확인 시,")
    class IsPayable {

        @Test
        @DisplayName("확정된 주문은 결제할 수 있다")
        void returnsTrue_whenOrderIsConfirmed() {
            // given
            Order order = OrderFixture.aConfirmedOrder();

            // when & then
            assertThat(order.isPayable()).isTrue();
        }

        @Test
        @DisplayName("접수만 된 주문은 결제할 수 없다")
        void returnsFalse_whenOrderIsPending() {
            // given
            Order order = OrderFixture.aDraftedOrder();

            // when & then
            assertThat(order.isPayable()).isFalse();
        }
    }

    @Nested
    @DisplayName("getLines - 라인 목록 조회 시,")
    class GetLines {

        @Test
        @DisplayName("반환된 라인 목록에 라인을 추가하면 UnsupportedOperationException이 발생한다")
        void returnsUnmodifiableLines() {
            // given
            Order order = OrderFixture.aConfirmedOrder();
            OrderLine newLine = OrderLine.of(ProductFixture.aSavedProduct(99L), 1);

            // when & then
            assertThatThrownBy(() -> order.getLines().add(newLine))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
