package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
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

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 재시도 대상은 PG가 요청을 처리하지 않았음이 확실한 실패뿐이고, 최대 1회 다시 보낸다
 * (참고: 07_payment.md E.3·E.4).
 */
class RetryingPaymentGatewayTest {

    private static final PaymentGatewayDto.ApprovalCommand COMMAND = new PaymentGatewayDto.ApprovalCommand(
            135135L, "20260828-A3F9K2QP", CardType.SAMSUNG, "1234-5678-9814-1451", Money.of(5_000L));
    private static final PaymentGatewayDto.Approval APPROVAL =
            new PaymentGatewayDto.Approval("20260828:TR:0a8ec1", PgTransactionStatus.PENDING);

    /** 실제로 기다리지 않도록 대기는 모두 0으로 둔다. */
    private static final PgProperties.RetryPolicy NO_WAIT_POLICY =
            new PgProperties.RetryPolicy(2, 0, 0, 0, 0, 0, 1_000);

    /** 정해 둔 순서대로 실패하거나 응답하는 가짜 PG. 몇 번 불렸는지 센다. */
    private static final class ScriptedGateway implements PaymentGateway {

        private final Deque<Object> outcomes;
        private int calls;

        ScriptedGateway(Object... outcomes) {
            this.outcomes = new ArrayDeque<>(List.of(outcomes));
        }

        @Override
        public PaymentGatewayDto.Approval requestApproval(PaymentGatewayDto.ApprovalCommand command) {
            calls++;
            Object outcome = outcomes.poll();
            if (outcome instanceof RuntimeException failure) {
                throw failure;
            }
            return (PaymentGatewayDto.Approval) outcome;
        }
    }

    @Nested
    @DisplayName("재시도하는 실패")
    class Retried {

        @Test
        @DisplayName("PG에 연결하지 못한 뒤 다시 보내 성공하면 그 결과를 돌려주고, PG를 두 번 부른다")
        void returnsApproval_whenConnectionFailsOnceThenSucceeds() {
            // given
            ScriptedGateway pg = new ScriptedGateway(new PgConnectionFailedException("PG에 연결하지 못했습니다."), APPROVAL);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, NO_WAIT_POLICY);

            // when
            PaymentGatewayDto.Approval approval = gateway.requestApproval(COMMAND);

            // then
            assertAll(
                    () -> assertThat(approval).isEqualTo(APPROVAL),
                    () -> assertThat(pg.calls).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("PG가 Retry-After 없이 503을 준 뒤 다시 보내 성공하면 그 결과를 돌려준다")
        void returnsApproval_whenUnavailableOnceThenSucceeds() {
            // given
            ScriptedGateway pg = new ScriptedGateway(new PgUnavailableException("PG가 요청을 받지 못했습니다.", null), APPROVAL);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, NO_WAIT_POLICY);

            // when
            PaymentGatewayDto.Approval approval = gateway.requestApproval(COMMAND);

            // then
            assertAll(
                    () -> assertThat(approval).isEqualTo(APPROVAL),
                    () -> assertThat(pg.calls).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("PG가 retryAfterCap 이하의 Retry-After로 429를 준 뒤 다시 보내 성공하면 그 결과를 돌려준다")
        void returnsApproval_whenRateLimitedWithinCapOnceThenSucceeds() {
            // given
            ScriptedGateway pg = new ScriptedGateway(
                    new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", Duration.ofMillis(10)), APPROVAL);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, NO_WAIT_POLICY);

            // when
            PaymentGatewayDto.Approval approval = gateway.requestApproval(COMMAND);

            // then
            assertAll(
                    () -> assertThat(approval).isEqualTo(APPROVAL),
                    () -> assertThat(pg.calls).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("maxAttempts 가 2일 때 두 번 모두 실패하면 더 보내지 않고 마지막 실패를 그대로 던진다")
        void throwsLastFailure_whenAllAttemptsFail() {
            // given
            PgConnectionFailedException lastFailure = new PgConnectionFailedException("PG에 연결하지 못했습니다.");
            ScriptedGateway pg = new ScriptedGateway(new PgConnectionFailedException("PG에 연결하지 못했습니다."), lastFailure);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, NO_WAIT_POLICY);

            // when
            Throwable thrown = catchThrowable(() -> gateway.requestApproval(COMMAND));

            // then
            assertAll(
                    () -> assertThat(thrown).isSameAs(lastFailure),
                    () -> assertThat(pg.calls).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("Retry-After가 retryAfterCap 과 같으면 상한 안이므로 다시 보낸다")
        void retries_whenRetryAfterEqualsCap() {
            // given
            Duration cap = Duration.ofMillis(10);
            PgProperties.RetryPolicy policy = new PgProperties.RetryPolicy(2, 0, 0, 0, 0, 0, cap.toMillis());
            ScriptedGateway pg = new ScriptedGateway(
                    new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", cap), APPROVAL);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, policy);

            // when
            PaymentGatewayDto.Approval approval = gateway.requestApproval(COMMAND);

            // then
            assertAll(
                    () -> assertThat(approval).isEqualTo(APPROVAL),
                    () -> assertThat(pg.calls).isEqualTo(2)
            );
        }

        /**
         * 대기 시간 계산은 아래 Interval 에서 따로 본다. 여기서는 계산한 시간을 실제로 재시도 사이에 쓰는지 본다.
         * 하한만 단언한다 — 기다린 시간은 그보다 짧을 수 없지만 길어지는 것은 환경 탓일 수 있다.
         */
        @Test
        @DisplayName("다시 보내기 전에 Retry-After 만큼 실제로 기다린다")
        void waitsRetryAfter_beforeRetrying() {
            // given
            Duration retryAfter = Duration.ofMillis(50);
            ScriptedGateway pg = new ScriptedGateway(
                    new PgUnavailableException("PG가 요청을 받지 못했습니다.", retryAfter), APPROVAL);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, NO_WAIT_POLICY);

            // when
            long startedAt = System.nanoTime();
            gateway.requestApproval(COMMAND);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            // then
            assertThat(elapsed).isGreaterThanOrEqualTo(retryAfter);
        }
    }

    @Nested
    @DisplayName("재시도하지 않는 실패")
    class NotRetried {

        @Test
        @DisplayName("PG 주소를 찾지 못하면 다시 보내도 같은 결과라 한 번만 부르고 그대로 던진다")
        void throwsWithoutRetry_whenHostIsUnresolved() {
            // given
            PgHostUnresolvedException failure = new PgHostUnresolvedException("PG 주소를 찾지 못했습니다.");
            ScriptedGateway pg = new ScriptedGateway(failure, APPROVAL);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, NO_WAIT_POLICY);

            // when
            Throwable thrown = catchThrowable(() -> gateway.requestApproval(COMMAND));

            // then
            assertAll(
                    () -> assertThat(thrown).isSameAs(failure),
                    () -> assertThat(pg.calls).isEqualTo(1)
            );
        }

        @Test
        @DisplayName("Retry-After가 retryAfterCap 보다 길면 기다리지 않고 한 번만 부르고 그대로 던진다")
        void throwsWithoutRetry_whenRetryAfterExceedsCap() {
            // given
            PgRateLimitedException failure = new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", Duration.ofSeconds(2));
            ScriptedGateway pg = new ScriptedGateway(failure, APPROVAL);
            RetryingPaymentGateway gateway = new RetryingPaymentGateway(pg, NO_WAIT_POLICY);

            // when
            Throwable thrown = catchThrowable(() -> gateway.requestApproval(COMMAND));

            // then
            assertAll(
                    () -> assertThat(thrown).isSameAs(failure),
                    () -> assertThat(pg.calls).isEqualTo(1)
            );
        }

        /**
         * 거절은 같은 요청이라 같은 답이고, 처리 여부를 모르는 실패는 다시 보내면 거래가 둘이 될 수 있다.
         */
        @Test
        @DisplayName("PG가 거절했거나 처리 여부를 알 수 없으면 한 번만 부르고 그대로 던진다")
        void throwsWithoutRetry_whenRejectedOrResultUnknown() {
            // given
            PgRejectedException rejected = new PgRejectedException("PG 승인 요청이 거절되었습니다.");
            PgResultUnknownException unknown = new PgResultUnknownException("PG 응답을 받지 못했습니다.");
            ScriptedGateway rejectingPg = new ScriptedGateway(rejected, APPROVAL);
            ScriptedGateway unknownPg = new ScriptedGateway(unknown, APPROVAL);

            // when
            Throwable thrownByRejected = catchThrowable(
                    () -> new RetryingPaymentGateway(rejectingPg, NO_WAIT_POLICY).requestApproval(COMMAND));
            Throwable thrownByUnknown = catchThrowable(
                    () -> new RetryingPaymentGateway(unknownPg, NO_WAIT_POLICY).requestApproval(COMMAND));

            // then
            assertAll(
                    () -> assertThat(thrownByRejected).isSameAs(rejected),
                    () -> assertThat(rejectingPg.calls).isEqualTo(1),
                    () -> assertThat(thrownByUnknown).isSameAs(unknown),
                    () -> assertThat(unknownPg.calls).isEqualTo(1)
            );
        }
    }

    /**
     * 대기 시간 계산만 따로 본다. 무작위는 항상 범위의 상한을 고르게 고정한다.
     */
    @Nested
    @DisplayName("다시 보내기 전 대기 시간")
    class Interval {

        private final PgProperties.RetryPolicy policy = new PgProperties.RetryPolicy(2, 200, 500, 700, 300, 500, 1_000);
        private final RetryingPaymentGateway gateway =
                new RetryingPaymentGateway(new ScriptedGateway(), policy, (min, max) -> max);

        @Test
        @DisplayName("연결 실패면 0부터 connectionFailedMaxWaitMillis 사이에서 고른다")
        void picksWithinConnectionFailedRange() {
            Duration interval = gateway.intervalFor(new PgConnectionFailedException("PG에 연결하지 못했습니다."));

            assertThat(interval).isEqualTo(Duration.ofMillis(200));
        }

        @Test
        @DisplayName("429에 Retry-After가 있으면 무작위 없이 그 시간만큼 기다린다")
        void followsRetryAfter_whenRateLimitedWithRetryAfter() {
            Duration interval = gateway.intervalFor(
                    new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", Duration.ofMillis(800)));

            assertThat(interval).isEqualTo(Duration.ofMillis(800));
        }

        @Test
        @DisplayName("429에 Retry-After가 없으면 rateLimitedMinWaitMillis 부터 rateLimitedMaxWaitMillis 사이에서 고른다")
        void picksWithinRateLimitedRange_whenRetryAfterIsAbsent() {
            Duration interval = gateway.intervalFor(new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", null));

            assertThat(interval).isEqualTo(Duration.ofMillis(700));
        }

        @Test
        @DisplayName("503에 Retry-After가 있으면 무작위 없이 그 시간만큼 기다린다")
        void followsRetryAfter_whenUnavailableWithRetryAfter() {
            Duration interval = gateway.intervalFor(
                    new PgUnavailableException("PG가 요청을 받지 못했습니다.", Duration.ofMillis(300)));

            assertThat(interval).isEqualTo(Duration.ofMillis(300));
        }

        @Test
        @DisplayName("503에 Retry-After가 없으면 unavailableMinWaitMillis 부터 unavailableMaxWaitMillis 사이에서 고른다")
        void picksWithinUnavailableRange_whenRetryAfterIsAbsent() {
            Duration interval = gateway.intervalFor(new PgUnavailableException("PG가 요청을 받지 못했습니다.", null));

            assertThat(interval).isEqualTo(Duration.ofMillis(500));
        }

        @Test
        @DisplayName("재시도 대상이 아닌 실패에는 대기 시간이 없다")
        void hasNoInterval_whenNotRetryable() {
            Duration interval = gateway.intervalFor(new PgRejectedException("PG 승인 요청이 거절되었습니다."));

            assertThat(interval).isEqualTo(Duration.ZERO);
        }

        /**
         * 다른 테스트는 무작위를 고정해 넣는다. 운영에서 쓰는 기본 무작위가 범위의 양 끝을 포함하는지는 여기서만 본다.
         */
        @Test
        @DisplayName("기본 무작위는 양 끝을 포함해 고르므로, 최소와 최대가 같으면 그 값이다")
        void defaultRandomIncludesBothEnds() {
            // given
            PgProperties.RetryPolicy samePolicy = new PgProperties.RetryPolicy(2, 200, 600, 600, 300, 500, 1_000);
            RetryingPaymentGateway defaultRandomGateway = new RetryingPaymentGateway(new ScriptedGateway(), samePolicy);

            // when
            Duration interval = defaultRandomGateway.intervalFor(
                    new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", null));

            // then
            assertThat(interval).isEqualTo(Duration.ofMillis(600));
        }
    }
}
