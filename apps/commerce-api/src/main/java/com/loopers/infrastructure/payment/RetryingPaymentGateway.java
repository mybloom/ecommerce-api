package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgConnectionFailedException;
import com.loopers.domain.payment.PgHostUnresolvedException;
import com.loopers.domain.payment.PgNotProcessedException;
import com.loopers.domain.payment.PgRateLimitedException;
import com.loopers.domain.payment.PgUnavailableException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongBinaryOperator;

/**
 * PG 요청을 감싸 <b>요청이 처리되지 않았음이 확실한 실패만</b> 한 번 더 보낸다. 무엇을 재시도할지와
 * 얼마나 기다릴지를 여기서 정하고, 숫자는 {@link PgProperties.RetryPolicy}에서 받는다
 * (참고: 07_payment.md E.3·E.4).
 * <p>
 * 거절({@code PgRejectedException})은 같은 요청이라 같은 답이고, 처리 여부를 모르는 실패
 * ({@code PgResultUnknownException})는 다시 보내면 거래가 둘이 될 수 있어 재시도하지 않는다.
 */
public class RetryingPaymentGateway implements PaymentGateway {

    private final PaymentGateway delegate;
    private final PgProperties.RetryPolicy policy;
    private final LongBinaryOperator randomBetween;
    private final Retry retry;

    public RetryingPaymentGateway(PaymentGateway delegate, PgProperties.RetryPolicy policy) {
        this(delegate, policy, (min, max) -> ThreadLocalRandom.current().nextLong(min, max + 1));
    }

    /**
     * @param randomBetween 양 끝을 포함하는 범위에서 하나를 고른다. 테스트가 대기 시간을 고정하려고 바꾼다
     */
    RetryingPaymentGateway(PaymentGateway delegate, PgProperties.RetryPolicy policy, LongBinaryOperator randomBetween) {
        this.delegate = delegate;
        this.policy = policy;
        this.randomBetween = randomBetween;
        this.retry = Retry.of("pg", RetryConfig.<PaymentGatewayDto.Approval>custom()
                .maxAttempts(policy.maxAttempts())
                .retryOnException(this::isRetryable)
                .intervalBiFunction((attempt, outcome) ->
                        outcome.isLeft() ? intervalFor(outcome.getLeft()).toMillis() : 0L)
                .build());
    }

    @Override
    public PaymentGatewayDto.Approval requestApproval(PaymentGatewayDto.ApprovalCommand command) {
        return Retry.decorateSupplier(retry, () -> delegate.requestApproval(command)).get();
    }

    /**
     * DNS 실패는 대개 설정 오류라 빠진다. Retry-After가 상한을 넘으면 사용자를 그만큼 붙잡지 않고 포기한다.
     * <p>
     * 처리되지 않은 실패는 sealed 타입이라 {@code switch}에 default가 없다. 원인이 늘면 여기서 컴파일 에러가 난다.
     */
    private boolean isRetryable(Throwable failure) {
        if (!(failure instanceof PgNotProcessedException notProcessed)) {
            return false;
        }
        return switch (notProcessed) {
            case PgConnectionFailedException ignored -> true;
            case PgHostUnresolvedException ignored -> false;
            case PgRateLimitedException rateLimited -> isWithinCap(rateLimited.getRetryAfter());
            case PgUnavailableException unavailable -> isWithinCap(unavailable.getRetryAfter());
        };
    }

    private boolean isWithinCap(@Nullable Duration retryAfter) {
        return retryAfter == null || retryAfter.toMillis() <= policy.retryAfterCapMillis();
    }

    /**
     * PG가 Retry-After를 주면 그 시간을 그대로 따른다. 없으면 범위 안에서 무작위로 골라, 같은 시각에 실패한
     * 요청들이 같은 간격으로 다시 몰리지 않게 한다.
     */
    Duration intervalFor(Throwable failure) {
        if (!(failure instanceof PgNotProcessedException notProcessed)) {
            return Duration.ZERO;
        }
        return switch (notProcessed) {
            case PgConnectionFailedException ignored -> randomMillis(0, policy.connectionFailedMaxWaitMillis());
            case PgHostUnresolvedException ignored -> Duration.ZERO;
            case PgRateLimitedException rateLimited -> retryAfterOr(rateLimited.getRetryAfter(),
                    policy.rateLimitedMinWaitMillis(), policy.rateLimitedMaxWaitMillis());
            case PgUnavailableException unavailable -> retryAfterOr(unavailable.getRetryAfter(),
                    policy.unavailableMinWaitMillis(), policy.unavailableMaxWaitMillis());
        };
    }

    private Duration retryAfterOr(@Nullable Duration retryAfter, long minMillis, long maxMillis) {
        return retryAfter != null ? retryAfter : randomMillis(minMillis, maxMillis);
    }

    private Duration randomMillis(long minMillis, long maxMillis) {
        return Duration.ofMillis(randomBetween.applyAsLong(minMillis, maxMillis));
    }
}
