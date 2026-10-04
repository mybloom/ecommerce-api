package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgCircuitOpenException;
import com.loopers.domain.payment.PgConnectionFailedException;
import com.loopers.domain.payment.PgHostUnresolvedException;
import com.loopers.domain.payment.PgRateLimitedException;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.payment.PgUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * PG 요청을 감싸 <b>PG의 건강을 보여 주는 실패만</b> 세고, 실패율이 임계치를 넘으면 한동안 PG를 부르지 않는다.
 * 숫자는 {@link PgProperties.CircuitBreakerPolicy}에서 받는다 (참고: 07_payment.md F.1·F.3).
 * <p>
 * 429는 우리 요청량 문제라 계산에서 뺀다. PG의 거절({@code PgRejectedException})은 PG가 정상적으로 판단한
 * 것이라 성공으로 센다 — 나열하지 않은 예외는 성공으로 세어진다.
 * <p>
 * 열린 회로의 거절은 {@link PgCircuitOpenException}으로 바꿔, resilience4j 타입이 infrastructure 밖으로
 * 나가지 않고 다른 "처리 안 됨"과 같이 실패로 확정되게 한다 (참고: F.2).
 */
@Slf4j
public class CircuitBreakingPaymentGateway implements PaymentGateway {

    private final PaymentGateway delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakingPaymentGateway(PaymentGateway delegate, PgProperties.CircuitBreakerPolicy policy) {
        this.delegate = delegate;
        this.circuitBreaker = CircuitBreaker.of("pg", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(policy.slidingWindowSize())
                .minimumNumberOfCalls(policy.minimumNumberOfCalls())
                .failureRateThreshold(policy.failureRateThreshold())
                .waitDurationInOpenState(Duration.ofMillis(policy.waitDurationInOpenStateMillis()))
                .permittedNumberOfCallsInHalfOpenState(policy.permittedNumberOfCallsInHalfOpenState())
                .recordExceptions(
                        PgConnectionFailedException.class,
                        PgHostUnresolvedException.class,
                        PgUnavailableException.class,
                        PgResultUnknownException.class)
                .ignoreExceptions(PgRateLimitedException.class)
                .build());
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("PG 서킷 상태가 바뀌었습니다. {}", event.getStateTransition()));
    }

    @Override
    public PaymentGatewayDto.Approval requestApproval(PaymentGatewayDto.ApprovalCommand command) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.requestApproval(command));
        } catch (CallNotPermittedException e) {
            throw new PgCircuitOpenException("PG 서킷이 열려 요청을 보내지 않았습니다.");
        }
    }
}
