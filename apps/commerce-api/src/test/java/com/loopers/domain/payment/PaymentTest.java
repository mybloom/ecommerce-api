package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentTest {

    @Nested
    @DisplayName("request - 결제 접수 시,")
    class Request {

        @Test
        @DisplayName("접수된 결제는 승인대기 상태로 주문·회원·수단·금액을 담고 승인 시각과 실패 사유는 없다")
        void createsPendingPayment() {
            // given
            Long orderId = PaymentFixture.DEFAULT_ORDER_ID;
            Long memberId = PaymentFixture.DEFAULT_MEMBER_ID;
            Money amount = PaymentFixture.DEFAULT_AMOUNT;

            // when
            Payment payment = Payment.request(orderId, memberId, PaymentMethod.POINT, amount);

            // then
            assertAll(
                    () -> assertThat(payment.getOrderId()).isEqualTo(orderId),
                    () -> assertThat(payment.getMemberId()).isEqualTo(memberId),
                    () -> assertThat(payment.getMethod()).isEqualTo(PaymentMethod.POINT),
                    () -> assertThat(payment.getAmount()).isEqualTo(amount),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getTransactionKey()).isNull(),
                    () -> assertThat(payment.getApprovedAt()).isNull(),
                    () -> assertThat(payment.getFailureReason()).isNull()
            );
        }

        @Test
        @DisplayName("결제 금액이 0이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenAmountIsZero() {
            // given
            Money zeroAmount = Money.ZERO;

            // when & then
            assertThatThrownBy(() -> Payment.request(
                    PaymentFixture.DEFAULT_ORDER_ID, PaymentFixture.DEFAULT_MEMBER_ID,
                    PaymentMethod.POINT, zeroAmount))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("approve - 결제 승인 시,")
    class Approve {

        @Test
        @DisplayName("승인완료로 전이하고 승인 시각을 기록하며 실패 사유는 남기지 않는다")
        void transitionsToApproved() {
            // given
            Payment payment = PaymentFixture.aRequestedPayment();

            // when
            payment.approve(null);

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED),
                    () -> assertThat(payment.getApprovedAt()).isNotNull(),
                    () -> assertThat(payment.getFailureReason()).isNull()
            );
        }

        @Test
        @DisplayName("포인트 결제는 PG 거래 식별자 없이 승인된다")
        void keepsTransactionKeyEmpty_whenMethodIsPoint() {
            // given
            Payment payment = PaymentFixture.aRequestedPayment();

            // when
            payment.approve(null);

            // then
            assertThat(payment.getTransactionKey()).isNull();
        }

        @Test
        @DisplayName("이미 승인된 결제를 다시 승인하면 CONFLICT 예외가 발생한다")
        void throwsConflict_whenPaymentIsAlreadyApproved() {
            // given
            Payment payment = PaymentFixture.anApprovedPayment();

            // when & then
            assertThatThrownBy(() -> payment.approve(null))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("실패한 결제는 승인할 수 없고 CONFLICT 예외가 발생한다")
        void throwsConflict_whenPaymentIsAlreadyFailed() {
            // given
            Payment payment = PaymentFixture.aFailedPayment();

            // when & then
            assertThatThrownBy(() -> payment.approve(null))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("fail - 결제 실패 확정 시,")
    class Fail {

        @Test
        @DisplayName("결제실패로 전이하고 실패 사유를 남기며 승인 시각은 기록하지 않는다")
        void transitionsToFailed() {
            // given
            Payment payment = PaymentFixture.aRequestedPayment();
            String reason = PaymentFixture.DEFAULT_FAILURE_REASON;

            // when
            payment.fail(reason);

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailureReason()).isEqualTo(reason),
                    () -> assertThat(payment.getApprovedAt()).isNull()
            );
        }

        @Test
        @DisplayName("실패 사유가 비어 있으면 BAD_REQUEST가 나고 결제는 승인대기로 남는다")
        void staysPending_whenReasonIsBlank() {
            // given
            Payment payment = PaymentFixture.aRequestedPayment();
            String blankReason = "  ";

            // when
            Throwable thrown = catchThrowable(() -> payment.fail(blankReason));

            // then
            assertAll(
                    () -> assertThat(thrown).isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING)
            );
        }

        @Test
        @DisplayName("이미 승인된 결제는 실패 처리할 수 없고 CONFLICT 예외가 발생한다")
        void throwsConflict_whenPaymentIsAlreadyApproved() {
            // given
            Payment payment = PaymentFixture.anApprovedPayment();

            // when & then
            assertThatThrownBy(() -> payment.fail(PaymentFixture.DEFAULT_FAILURE_REASON))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("isFinalized - 종결 여부 확인 시,")
    class IsFinalized {

        @Test
        @DisplayName("접수만 된 결제는 종결되지 않았다")
        void returnsFalse_whenPaymentIsPending() {
            // given
            Payment payment = PaymentFixture.aRequestedPayment();

            // when & then
            assertThat(payment.isFinalized()).isFalse();
        }

        @Test
        @DisplayName("승인된 결제는 종결되었다")
        void returnsTrue_whenPaymentIsApproved() {
            // given
            Payment payment = PaymentFixture.anApprovedPayment();

            // when & then
            assertThat(payment.isFinalized()).isTrue();
        }
    }

    @Nested
    @DisplayName("isOwnedBy - 소유권 확인 시,")
    class IsOwnedBy {

        @Test
        @DisplayName("결제한 회원이면 true를 반환한다")
        void returnsTrue_whenMemberIsOwner() {
            // given
            Long ownerId = 42L;
            Payment payment = PaymentFixture.aRequestedPaymentOf(ownerId);

            // when & then
            assertThat(payment.isOwnedBy(ownerId)).isTrue();
        }

        @Test
        @DisplayName("다른 회원이면 false를 반환한다")
        void returnsFalse_whenMemberIsNotOwner() {
            // given
            Long ownerId = 42L;
            Long otherMemberId = 99L;
            Payment payment = PaymentFixture.aRequestedPaymentOf(ownerId);

            // when & then
            assertThat(payment.isOwnedBy(otherMemberId)).isFalse();
        }
    }
}
