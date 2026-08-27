package com.loopers.domain.payment;

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

class PaymentStatusTest {

    @Nested
    @DisplayName("isFinalized - 종결 여부 확인 시,")
    class IsFinalized {

        @DisplayName("승인완료·결제실패는 종결된 상태다")
        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"APPROVED", "FAILED"})
        void returnsTrue_whenStatusIsFinalized(PaymentStatus finalizedStatus) {
            assertThat(finalizedStatus.isFinalized()).isTrue();
        }

        @Test
        @DisplayName("승인대기는 종결되지 않은 상태다")
        void returnsFalse_whenStatusIsPending() {
            assertThat(PaymentStatus.PENDING.isFinalized()).isFalse();
        }
    }

    @Nested
    @DisplayName("requireTransitionTo - 상태 전이 검사 시,")
    class RequireTransitionTo {

        @DisplayName("승인대기에서 승인완료·결제실패로 가는 전이는 아무 일도 일어나지 않는다")
        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"APPROVED", "FAILED"})
        void passes_whenPendingTransitionsToResult(PaymentStatus resultStatus) {
            assertThatCode(() -> PaymentStatus.PENDING.requireTransitionTo(resultStatus))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("승인대기에서 승인대기로 가는 전이는 CONFLICT 예외가 발생한다")
        void throwsConflict_whenPendingTransitionsToItself() {
            assertThatThrownBy(() -> PaymentStatus.PENDING.requireTransitionTo(PaymentStatus.PENDING))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        /**
         * 목표 상태를 전부 훑는다. "어디로도 갈 수 없다"는 주장을 일부만 검사하면,
         * 나중에 그 상태에서 나가는 전이가 열려도 초록불이 그대로 유지된다.
         */
        @DisplayName("종결된 상태에서는 어떤 상태로도 전이할 수 없고 전부 CONFLICT 예외가 발생한다")
        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"APPROVED", "FAILED"})
        void throwsConflict_whenSourceIsFinalized(PaymentStatus finalizedStatus) {
            // given
            PaymentStatus[] allTargets = PaymentStatus.values();

            // when & then
            assertAll(Arrays.stream(allTargets)
                    .map(target -> (Executable) () ->
                            assertThatThrownBy(() -> finalizedStatus.requireTransitionTo(target))
                                    .isInstanceOfSatisfying(CoreException.class, e ->
                                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT))));
        }

        @Test
        @DisplayName("예외 메시지는 출발 상태와 목표 상태의 label을 모두 담는다")
        void messageContainsBothLabels() {
            // given
            PaymentStatus source = PaymentStatus.APPROVED;
            PaymentStatus target = PaymentStatus.FAILED;

            // when & then
            assertThatThrownBy(() -> source.requireTransitionTo(target))
                    .hasMessageContaining(source.getLabel())
                    .hasMessageContaining(target.getLabel());
        }
    }
}
