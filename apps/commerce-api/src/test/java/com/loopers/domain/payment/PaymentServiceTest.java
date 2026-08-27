package com.loopers.domain.payment;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository);
    }

    @Nested
    @DisplayName("request - 결제 접수 시,")
    class Request {

        @Test
        @DisplayName("승인대기 상태로 저장하고 저장된 결제를 반환한다")
        void savesPendingPayment() {
            // given
            Long orderId = PaymentFixture.DEFAULT_ORDER_ID;
            Long memberId = PaymentFixture.DEFAULT_MEMBER_ID;
            Money amount = PaymentFixture.DEFAULT_AMOUNT;
            PaymentServiceDto.RequestCommand command =
                    new PaymentServiceDto.RequestCommand(orderId, memberId, PaymentMethod.POINT, amount);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Payment result = paymentService.request(command);

            // then
            assertAll(
                    () -> assertThat(result.getOrderId()).isEqualTo(orderId),
                    () -> assertThat(result.getMemberId()).isEqualTo(memberId),
                    () -> assertThat(result.getMethod()).isEqualTo(PaymentMethod.POINT),
                    () -> assertThat(result.getAmount()).isEqualTo(amount),
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING)
            );
        }
    }

    @Nested
    @DisplayName("approve - 결제 승인 시,")
    class Approve {

        @Test
        @DisplayName("결제를 찾아 승인완료로 만들고 승인 시각을 기록한다")
        void approvesPayment() {
            // given
            Long paymentId = 1L;
            Payment requestedPayment = PaymentFixture.aRequestedPayment();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(requestedPayment));

            // when
            Payment result = paymentService.approve(new PaymentServiceDto.ApproveCommand(paymentId, null));

            // then
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED),
                    () -> assertThat(result.getApprovedAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("결제가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenPaymentDoesNotExist() {
            // given
            Long nonExistentPaymentId = Long.MAX_VALUE;
            PaymentServiceDto.ApproveCommand command =
                    new PaymentServiceDto.ApproveCommand(nonExistentPaymentId, null);
            when(paymentRepository.findById(nonExistentPaymentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.approve(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("fail - 결제 실패 확정 시,")
    class Fail {

        @Test
        @DisplayName("결제를 찾아 결제실패로 만들고 실패 사유를 남긴다")
        void failsPayment() {
            // given
            Long paymentId = 1L;
            String reason = PaymentFixture.DEFAULT_FAILURE_REASON;
            Payment requestedPayment = PaymentFixture.aRequestedPayment();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(requestedPayment));

            // when
            Payment result = paymentService.fail(new PaymentServiceDto.FailCommand(paymentId, reason));

            // then
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(result.getFailureReason()).isEqualTo(reason),
                    () -> assertThat(result.getApprovedAt()).isNull()
            );
        }

        @Test
        @DisplayName("결제가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenPaymentDoesNotExist() {
            // given
            Long nonExistentPaymentId = Long.MAX_VALUE;
            PaymentServiceDto.FailCommand command = new PaymentServiceDto.FailCommand(
                    nonExistentPaymentId, PaymentFixture.DEFAULT_FAILURE_REASON);
            when(paymentRepository.findById(nonExistentPaymentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.fail(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
