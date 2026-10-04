package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgCircuitOpenException;
import com.loopers.domain.payment.PgConnectionFailedException;
import com.loopers.domain.payment.PgHostUnresolvedException;
import com.loopers.domain.payment.PgRateLimitedException;
import com.loopers.domain.payment.PgRejectedException;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.payment.PgUnavailableException;
import com.loopers.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * PG의 건강을 보여 주는 실패만 세고, 회로가 열리면 PG를 부르지 않고 처리되지 않은 실패로 끝낸다
 * (참고: 07_payment.md F.1·F.2).
 */
class CircuitBreakingPaymentGatewayTest {

    private static final PaymentGatewayDto.ApprovalCommand COMMAND = new PaymentGatewayDto.ApprovalCommand(
            135135L, "20260828-A3F9K2QP", CardType.SAMSUNG, "1234-5678-9814-1451", Money.of(5_000L));
    private static final PaymentGatewayDto.Approval APPROVAL =
            new PaymentGatewayDto.Approval("20260828:TR:0a8ec1", PgTransactionStatus.PENDING);

    private static final int SLIDING_WINDOW_SIZE = 10;
    private static final int MINIMUM_NUMBER_OF_CALLS = 4;
    /** resilience4j 기본값(50)과 다르게 둔다. 같으면 설정을 빠뜨려도 테스트가 모른다 */
    private static final int FAILURE_RATE_THRESHOLD = 60;
    /** 열린 회로가 테스트 도중 반열림으로 넘어가지 않을 만큼 길게 둔다 */
    private static final long LONG_WAIT_MILLIS = 60_000;
    private static final int PERMITTED_IN_HALF_OPEN = 2;

    private static final PgProperties.CircuitBreakerPolicy POLICY = new PgProperties.CircuitBreakerPolicy(
            SLIDING_WINDOW_SIZE, MINIMUM_NUMBER_OF_CALLS, FAILURE_RATE_THRESHOLD, LONG_WAIT_MILLIS, PERMITTED_IN_HALF_OPEN);

    /** 다음에 무엇을 할지 테스트가 정하는 가짜 PG. 몇 번 불렸는지 센다. */
    private static final class SwitchableGateway implements PaymentGateway {

        private Supplier<PaymentGatewayDto.Approval> next = () -> APPROVAL;
        private int calls;

        void failWith(RuntimeException failure) {
            next = () -> {
                throw failure;
            };
        }

        void succeed() {
            next = () -> APPROVAL;
        }

        @Override
        public PaymentGatewayDto.Approval requestApproval(PaymentGatewayDto.ApprovalCommand command) {
            calls++;
            return next.get();
        }
    }

    private static void callTimes(PaymentGateway gateway, int times) {
        for (int i = 0; i < times; i++) {
            catchThrowable(() -> gateway.requestApproval(COMMAND));
        }
    }

    private static boolean isOpen(CircuitBreakingPaymentGateway gateway, SwitchableGateway pg) {
        int callsBefore = pg.calls;
        Throwable thrown = catchThrowable(() -> gateway.requestApproval(COMMAND));
        return thrown instanceof PgCircuitOpenException && pg.calls == callsBefore;
    }

    static Stream<Arguments> recordedFailures() {
        return Stream.of(
                Arguments.of(new PgConnectionFailedException("PG에 연결하지 못했습니다.")),
                Arguments.of(new PgHostUnresolvedException("PG 주소를 찾지 못했습니다.")),
                Arguments.of(new PgUnavailableException("PG가 요청을 받지 못했습니다.", null)),
                Arguments.of(new PgResultUnknownException("PG 응답을 받지 못했습니다."))
        );
    }

    @Nested
    @DisplayName("실패로 세는 것")
    class Recorded {

        @ParameterizedTest
        @MethodSource("com.loopers.infrastructure.payment.CircuitBreakingPaymentGatewayTest#recordedFailures")
        @DisplayName("failure 가 minimumNumberOfCalls 만큼 쌓이면 회로가 열려, 다음 요청은 PG를 부르지 않고 서킷이 열린 실패로 끝난다")
        void opens_whenRecordedFailuresReachMinimum(RuntimeException failure) {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, POLICY);
            pg.failWith(failure);
            callTimes(gateway, MINIMUM_NUMBER_OF_CALLS);

            // when
            Throwable thrown = catchThrowable(() -> gateway.requestApproval(COMMAND));

            // then
            assertAll(
                    () -> assertThat(thrown).isInstanceOf(PgCircuitOpenException.class),
                    () -> assertThat(pg.calls).isEqualTo(MINIMUM_NUMBER_OF_CALLS)
            );
        }

        @Test
        @DisplayName("회로가 닫혀 있으면 PG의 실패를 바꾸지 않고 그대로 던진다")
        void throwsPgFailureAsIs_whenClosed() {
            // given
            PgConnectionFailedException failure = new PgConnectionFailedException("PG에 연결하지 못했습니다.");
            SwitchableGateway pg = new SwitchableGateway();
            pg.failWith(failure);
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, POLICY);

            // when
            Throwable thrown = catchThrowable(() -> gateway.requestApproval(COMMAND));

            // then
            assertThat(thrown).isSameAs(failure);
        }

        @Test
        @DisplayName("slidingWindowSize 가 4일 때 성공 4번 뒤 실패 3번이면 최근 4건의 실패율이 75%라 열린다")
        void looksOnlyAtRecentCalls_withinSlidingWindowSize() {
            // given
            int slidingWindowSize = 4;
            PgProperties.CircuitBreakerPolicy policy = new PgProperties.CircuitBreakerPolicy(
                    slidingWindowSize, MINIMUM_NUMBER_OF_CALLS, FAILURE_RATE_THRESHOLD, LONG_WAIT_MILLIS, PERMITTED_IN_HALF_OPEN);
            SwitchableGateway pg = new SwitchableGateway();
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, policy);

            // when
            callTimes(gateway, 4);
            pg.failWith(new PgConnectionFailedException("PG에 연결하지 못했습니다."));
            callTimes(gateway, 3);

            // then
            assertThat(isOpen(gateway, pg)).isTrue();
        }

        @Test
        @DisplayName("failureRateThreshold 가 60일 때 성공 4번 뒤 실패 5번이면 실패율이 56%라 닫혀 있다")
        void staysClosed_whenFailureRateIsBelowThreshold() {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, POLICY);

            // when
            callTimes(gateway, 4);
            pg.failWith(new PgConnectionFailedException("PG에 연결하지 못했습니다."));
            callTimes(gateway, 5);

            // then
            assertThat(isOpen(gateway, pg)).isFalse();
        }
    }

    @Nested
    @DisplayName("실패로 세지 않는 것")
    class NotRecorded {

        @Test
        @DisplayName("429만 minimumNumberOfCalls 넘게 쌓여도 회로가 열리지 않는다")
        void staysClosed_whenOnlyRateLimited() {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            pg.failWith(new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", null));
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, POLICY);

            // when
            callTimes(gateway, MINIMUM_NUMBER_OF_CALLS * 2);

            // then
            assertThat(isOpen(gateway, pg)).isFalse();
        }

        /**
         * 429를 성공으로 세면 성공 5건 중 실패 3건이라 37.5%로 닫혀 있다. 계산에서 빼야 실패 3건 중 3건이 남아 열린다.
         */
        @Test
        @DisplayName("429는 성공으로도 세지 않아, 429 네 번 뒤 성공 1번과 실패 3번이면 실패율이 75%라 열린다")
        void excludesRateLimitedFromCalculation() {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, POLICY);

            // when
            pg.failWith(new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", null));
            callTimes(gateway, 4);
            pg.succeed();
            callTimes(gateway, 1);
            pg.failWith(new PgConnectionFailedException("PG에 연결하지 못했습니다."));
            callTimes(gateway, 3);

            // then
            assertThat(isOpen(gateway, pg)).isTrue();
        }

        /**
         * 거절을 빼기만 하면 실패 4건 중 4건이라 열린다. 성공으로 세야 9건 중 4건(44%)으로 닫혀 있다.
         */
        @Test
        @DisplayName("PG의 거절은 성공으로 세, 거절 5번 뒤 실패 4번이면 실패율이 44%라 닫혀 있다")
        void countsRejectedAsSuccess() {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, POLICY);

            // when
            pg.failWith(new PgRejectedException("PG 승인 요청이 거절되었습니다."));
            callTimes(gateway, 5);
            pg.failWith(new PgConnectionFailedException("PG에 연결하지 못했습니다."));
            callTimes(gateway, 4);

            // then
            assertThat(isOpen(gateway, pg)).isFalse();
        }
    }

    /**
     * 열림 유지 시간을 실제로 기다린다. 길게 기다리지 않도록 짧은 값을 넣는다.
     */
    @Nested
    @DisplayName("열린 뒤")
    class AfterOpen {

        private static final long SHORT_WAIT_MILLIS = 50;

        private final PgProperties.CircuitBreakerPolicy shortWaitPolicy = new PgProperties.CircuitBreakerPolicy(
                SLIDING_WINDOW_SIZE, MINIMUM_NUMBER_OF_CALLS, FAILURE_RATE_THRESHOLD, SHORT_WAIT_MILLIS, PERMITTED_IN_HALF_OPEN);

        @Test
        @DisplayName("waitDurationInOpenStateMillis 가 지나기 전에는 PG를 부르지 않는다")
        void rejects_beforeWaitDuration() {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            pg.failWith(new PgConnectionFailedException("PG에 연결하지 못했습니다."));
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, POLICY);
            callTimes(gateway, MINIMUM_NUMBER_OF_CALLS);
            pg.succeed();

            // when, then
            assertThat(isOpen(gateway, pg)).isTrue();
        }

        @Test
        @DisplayName("waitDurationInOpenStateMillis 가 지나면 다시 PG를 불러 결과를 돌려준다")
        void callsPgAgain_afterWaitDuration() throws InterruptedException {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            pg.failWith(new PgConnectionFailedException("PG에 연결하지 못했습니다."));
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, shortWaitPolicy);
            callTimes(gateway, MINIMUM_NUMBER_OF_CALLS);
            pg.succeed();
            Thread.sleep(SHORT_WAIT_MILLIS * 2);

            // when
            PaymentGatewayDto.Approval approval = gateway.requestApproval(COMMAND);

            // then
            assertAll(
                    () -> assertThat(approval).isEqualTo(APPROVAL),
                    () -> assertThat(pg.calls).isEqualTo(MINIMUM_NUMBER_OF_CALLS + 1)
            );
        }

        /**
         * 시험 건수가 설정보다 크게 잡히면 두 번 실패한 뒤에도 반열림이라 세 번째 요청이 PG로 나간다.
         */
        @Test
        @DisplayName("permittedNumberOfCallsInHalfOpenState 가 2일 때 반열림에서 두 번 모두 실패하면 다시 열려 PG를 부르지 않는다")
        void reopens_whenHalfOpenTrialsFail() throws InterruptedException {
            // given
            SwitchableGateway pg = new SwitchableGateway();
            pg.failWith(new PgConnectionFailedException("PG에 연결하지 못했습니다."));
            CircuitBreakingPaymentGateway gateway = new CircuitBreakingPaymentGateway(pg, shortWaitPolicy);
            callTimes(gateway, MINIMUM_NUMBER_OF_CALLS);
            Thread.sleep(SHORT_WAIT_MILLIS * 2);

            // when
            callTimes(gateway, PERMITTED_IN_HALF_OPEN);

            // then
            assertAll(
                    () -> assertThat(pg.calls).isEqualTo(MINIMUM_NUMBER_OF_CALLS + PERMITTED_IN_HALF_OPEN),
                    () -> assertThat(isOpen(gateway, pg)).isTrue()
            );
        }
    }
}
