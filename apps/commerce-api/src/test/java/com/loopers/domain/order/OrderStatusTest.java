package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

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

        @DisplayName("접수 상태에서 결제대기·주문실패로 가는 전이는 아무 일도 일어나지 않는다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "ORDER_FAILED"})
        void passes_whenPendingTransitionsToReachableStatus(OrderStatus reachableStatus) {
            assertThatCode(() -> OrderStatus.PENDING.requireTransitionTo(reachableStatus))
                    .doesNotThrowAnyException();
        }

        @DisplayName("접수 상태에서 결제완료·결제실패·접수로 가는 전이는 CONFLICT 예외가 발생한다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PAID", "PAYMENT_FAILED", "PENDING"})
        void throwsConflict_whenPendingTransitionsToUnreachableStatus(OrderStatus unreachableStatus) {
            assertThatThrownBy(() -> OrderStatus.PENDING.requireTransitionTo(unreachableStatus))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("결제대기 상태에서 결제완료·결제실패로 가는 전이는 아무 일도 일어나지 않는다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PAID", "PAYMENT_FAILED"})
        void passes_whenAwaitingPaymentTransitionsToPaymentResult(OrderStatus paymentResultStatus) {
            assertThatCode(() -> OrderStatus.AWAITING_PAYMENT.requireTransitionTo(paymentResultStatus))
                    .doesNotThrowAnyException();
        }

        @DisplayName("결제대기 상태에서 결제 결과가 아닌 상태로 가는 전이는 CONFLICT 예외가 발생한다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "AWAITING_PAYMENT", "ORDER_FAILED"})
        void throwsConflict_whenAwaitingPaymentTransitionsElsewhere(OrderStatus unreachableStatus) {
            assertThatThrownBy(() -> OrderStatus.AWAITING_PAYMENT.requireTransitionTo(unreachableStatus))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        /**
         * 목표 상태를 전부 훑는다. "어디로도 갈 수 없다"는 주장을 일부만 검사하면,
         * 나중에 그 상태에서 나가는 전이가 열려도 초록불이 그대로 유지된다.
         */
        @DisplayName("종결된 상태에서는 어떤 상태로도 전이할 수 없고 전부 CONFLICT 예외가 발생한다")
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"ORDER_FAILED", "PAID", "PAYMENT_FAILED"})
        void throwsConflict_whenSourceIsTerminal(OrderStatus terminalStatus) {
            // given
            OrderStatus[] allTargets = OrderStatus.values();

            // when & then
            assertAll(Arrays.stream(allTargets)
                    .map(target -> (Executable) () ->
                            assertThatThrownBy(() -> terminalStatus.requireTransitionTo(target))
                                    .isInstanceOfSatisfying(CoreException.class, e ->
                                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT))));
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
