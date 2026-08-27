package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderStatusTest {

    @Nested
    @DisplayName("isPayable - 결제 가능 여부 확인 시,")
    class IsPayable {

        @Test
        @DisplayName("결제대기 상태만 결제를 받을 수 있다")
        void returnsTrue_whenStatusIsAwaitingPayment() {
            assertThat(OrderStatus.AWAITING_PAYMENT.isPayable()).isTrue();
        }

        @DisplayName("그 외 상태는 결제를 받을 수 없다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = "AWAITING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
        void returnsFalse_whenStatusIsNotAwaitingPayment(OrderStatus notPayableStatus) {
            assertThat(notPayableStatus.isPayable()).isFalse();
        }
    }

    @Nested
    @DisplayName("isVisibleToUser - 사용자 노출 여부 확인 시,")
    class IsVisibleToUser {

        @DisplayName("접수·주문실패는 사용자 조회에 노출하지 않는다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "ORDER_FAILED"})
        void returnsFalse_whenOrderIsNotEstablished(OrderStatus hiddenStatus) {
            assertThat(hiddenStatus.isVisibleToUser()).isFalse();
        }

        @DisplayName("결제대기·결제완료·결제실패는 사용자 조회에 노출한다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "PAID", "PAYMENT_FAILED"})
        void returnsTrue_whenOrderIsEstablished(OrderStatus visibleStatus) {
            assertThat(visibleStatus.isVisibleToUser()).isTrue();
        }
    }

    @Nested
    @DisplayName("requireTransitionTo - 상태 전이 검사 시,")
    class RequireTransitionTo {

        @DisplayName("허용된 전이면 아무 일도 일어나지 않는다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "ORDER_FAILED"})
        void passes_whenTransitionIsAllowed(OrderStatus reachableStatus) {
            assertThatCode(() -> OrderStatus.PENDING.requireTransitionTo(reachableStatus))
                    .doesNotThrowAnyException();
        }

        @DisplayName("허용되지 않은 전이면 CONFLICT 예외가 발생한다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PAID", "PAYMENT_FAILED", "PENDING"})
        void throwsConflict_whenTransitionIsNotAllowed(OrderStatus unreachableStatus) {
            assertThatThrownBy(() -> OrderStatus.PENDING.requireTransitionTo(unreachableStatus))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("접수가 아닌 상태에서는 어디로도 전이할 수 없고 CONFLICT 예외가 발생한다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
        void throwsConflict_whenSourceIsNotPending(OrderStatus terminalStatus) {
            assertAll(
                    () -> assertThatThrownBy(() -> terminalStatus.requireTransitionTo(OrderStatus.AWAITING_PAYMENT))
                            .isInstanceOfSatisfying(CoreException.class, e ->
                                    assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT)),
                    () -> assertThatThrownBy(() -> terminalStatus.requireTransitionTo(OrderStatus.ORDER_FAILED))
                            .isInstanceOfSatisfying(CoreException.class, e ->
                                    assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT))
            );
        }

        @Test
        @DisplayName("예외 메시지는 출발 상태와 목표 상태의 label을 모두 담는다")
        void messageContainsBothLabels() {
            // given
            OrderStatus source = OrderStatus.AWAITING_PAYMENT;
            OrderStatus target = OrderStatus.ORDER_FAILED;

            // when & then
            assertThatThrownBy(() -> source.requireTransitionTo(target))
                    .hasMessageContaining(source.getLabel())
                    .hasMessageContaining(target.getLabel());
        }
    }
}
