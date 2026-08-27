package com.loopers.domain.order;

import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository);
    }

    @Nested
    @DisplayName("findByOrderNumber - 주문번호로 조회 시,")
    class FindByOrderNumber {

        @Test
        @DisplayName("해당 주문번호의 주문을 반환한다")
        void returnsOrder_whenOrderNumberExists() {
            // given
            OrderNumber orderNumber = OrderFixture.DEFAULT_ORDER_NUMBER;
            Order savedOrder = OrderFixture.aConfirmedOrder();
            OrderServiceDto.FindByOrderNumberCommand command =
                    new OrderServiceDto.FindByOrderNumberCommand(orderNumber);
            when(orderRepository.findByOrderNumber(orderNumber)).thenReturn(Optional.of(savedOrder));

            // when
            Order result = orderService.findByOrderNumber(command);

            // then
            assertThat(result).isSameAs(savedOrder);
        }

        @Test
        @DisplayName("주문이 없으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenOrderNumberDoesNotExist() {
            // given
            OrderNumber unknownOrderNumber = OrderNumber.of("20260827-ZZZZZZZZ");
            OrderServiceDto.FindByOrderNumberCommand command =
                    new OrderServiceDto.FindByOrderNumberCommand(unknownOrderNumber);
            when(orderRepository.findByOrderNumber(unknownOrderNumber)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.findByOrderNumber(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("draft - 주문 접수 시,")
    class Draft {

        @Test
        @DisplayName("주문번호를 채번해 접수 상태로 저장하고, 저장된 주문을 반환한다")
        void savesPendingOrderWithGeneratedOrderNumber() {
            // given
            Long memberId = OrderFixture.DEFAULT_MEMBER_ID;
            IdempotencyKey idempotencyKey = OrderFixture.DEFAULT_IDEMPOTENCY_KEY;
            OrderServiceDto.DraftCommand command =
                    new OrderServiceDto.DraftCommand(memberId, idempotencyKey);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Order result = orderService.draft(command);

            // then
            assertAll(
                    () -> assertThat(result.getMemberId()).isEqualTo(memberId),
                    () -> assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey),
                    () -> assertThat(result.getOrderNumber()).isNotNull(),
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(result.getTotalAmount()).isEqualTo(Money.ZERO),
                    () -> assertThat(result.getLines()).isEmpty()
            );
        }

        @Test
        @DisplayName("접수할 때마다 서로 다른 주문번호가 채번된다")
        void generatesDistinctOrderNumber_perDraft() {
            // given
            OrderServiceDto.DraftCommand command = new OrderServiceDto.DraftCommand(
                    OrderFixture.DEFAULT_MEMBER_ID, OrderFixture.DEFAULT_IDEMPOTENCY_KEY);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Order first = orderService.draft(command);
            Order second = orderService.draft(command);

            // then
            assertThat(first.getOrderNumber()).isNotEqualTo(second.getOrderNumber());
        }
    }

    @Nested
    @DisplayName("findByIdempotencyKey - 멱등키로 주문 조회 시,")
    class FindByIdempotencyKey {

        @Test
        @DisplayName("같은 키로 접수된 주문이 있으면 그 주문을 담은 Optional을 반환한다")
        void returnsOrder_whenKeyAlreadyUsed() {
            // given
            IdempotencyKey idempotencyKey = OrderFixture.DEFAULT_IDEMPOTENCY_KEY;
            Order existingOrder = OrderFixture.aDraftedOrder();
            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

            // when
            Optional<Order> result = orderService.findByIdempotencyKey(
                    new OrderServiceDto.FindByIdempotencyKeyCommand(idempotencyKey));

            // then
            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.orElseThrow().getIdempotencyKey()).isEqualTo(idempotencyKey)
            );
        }

        @Test
        @DisplayName("처음 쓰는 키면 빈 Optional을 반환한다")
        void returnsEmpty_whenKeyIsNew() {
            // given
            IdempotencyKey unusedKey = IdempotencyKey.of("unused-key-0001");
            when(orderRepository.findByIdempotencyKey(unusedKey)).thenReturn(Optional.empty());

            // when
            Optional<Order> result = orderService.findByIdempotencyKey(
                    new OrderServiceDto.FindByIdempotencyKeyCommand(unusedKey));

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("confirm - 주문 확정 시,")
    class Confirm {

        @Test
        @DisplayName("주문을 찾아 라인을 붙이고 총액을 확정해 결제대기 상태로 만든다")
        void confirmsOrder() {
            // given
            Long orderId = 1L;
            Order draftedOrder = OrderFixture.aDraftedOrder();
            List<OrderLine> lines = OrderFixture.aLines();
            Money expectedTotal = Money.of(
                    ProductFixture.DEFAULT_PRICE.getAmount() * OrderFixture.DEFAULT_QUANTITY);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(draftedOrder));

            // when
            Order result = orderService.confirm(new OrderServiceDto.ConfirmCommand(orderId, lines));

            // then
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(result.getTotalAmount()).isEqualTo(expectedTotal),
                    () -> assertThat(result.getLines()).hasSize(1)
            );
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenOrderDoesNotExist() {
            // given
            Long nonExistentOrderId = Long.MAX_VALUE;
            List<OrderLine> lines = OrderFixture.aLines();
            OrderServiceDto.ConfirmCommand command =
                    new OrderServiceDto.ConfirmCommand(nonExistentOrderId, lines);
            when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.confirm(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("markOrderFailed - 확정 실패 처리 시,")
    class MarkOrderFailed {

        @Test
        @DisplayName("주문을 찾아 주문실패 상태로 만들고, 라인과 총액은 비어 있는 채로 둔다")
        void marksOrderFailed() {
            // given
            Long orderId = 1L;
            Order draftedOrder = OrderFixture.aDraftedOrder();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(draftedOrder));

            // when
            Order result = orderService.markOrderFailed(new OrderServiceDto.MarkOrderFailedCommand(orderId));

            // then
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.ORDER_FAILED),
                    () -> assertThat(result.getLines()).isEmpty(),
                    () -> assertThat(result.getTotalAmount()).isEqualTo(Money.ZERO)
            );
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenOrderDoesNotExist() {
            // given
            Long nonExistentOrderId = Long.MAX_VALUE;
            OrderServiceDto.MarkOrderFailedCommand command =
                    new OrderServiceDto.MarkOrderFailedCommand(nonExistentOrderId);
            when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.markOrderFailed(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("pay - 결제 완료 처리 시,")
    class Pay {

        @Test
        @DisplayName("주문을 찾아 결제완료 상태로 만들고 결제 시각을 기록한다")
        void marksOrderPaid() {
            // given
            Long orderId = 1L;
            Order confirmedOrder = OrderFixture.aConfirmedOrder();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));

            // when
            Order result = orderService.pay(new OrderServiceDto.PayCommand(orderId));

            // then
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID),
                    () -> assertThat(result.getPaidAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenOrderDoesNotExist() {
            // given
            Long nonExistentOrderId = Long.MAX_VALUE;
            OrderServiceDto.PayCommand command = new OrderServiceDto.PayCommand(nonExistentOrderId);
            when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.pay(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("markPaymentFailed - 결제 실패 처리 시,")
    class MarkPaymentFailed {

        @Test
        @DisplayName("주문을 찾아 결제실패 상태로 만들고 라인과 총액은 그대로 둔다")
        void marksPaymentFailed() {
            // given
            Long orderId = 1L;
            Order confirmedOrder = OrderFixture.aConfirmedOrder();
            Money totalAmountBeforeFailure = confirmedOrder.getTotalAmount();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));

            // when
            Order result = orderService.markPaymentFailed(new OrderServiceDto.MarkPaymentFailedCommand(orderId));

            // then
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(result.getLines()).hasSize(1),
                    () -> assertThat(result.getTotalAmount()).isEqualTo(totalAmountBeforeFailure),
                    () -> assertThat(result.getPaidAt()).isNull()
            );
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenOrderDoesNotExist() {
            // given
            Long nonExistentOrderId = Long.MAX_VALUE;
            OrderServiceDto.MarkPaymentFailedCommand command =
                    new OrderServiceDto.MarkPaymentFailedCommand(nonExistentOrderId);
            when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderService.markPaymentFailed(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
