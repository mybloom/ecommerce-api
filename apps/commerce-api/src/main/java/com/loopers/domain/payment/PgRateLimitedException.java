package com.loopers.domain.payment;

import com.loopers.support.error.ErrorType;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * PG가 429로 요청을 받지 않았다. 우리 서버가 PG 한도를 넘게 보냈다는 신호다.
 * <p>
 * PG가 알려 준 대기 시간({@code Retry-After})을 담는다. 재시도는 이 시간을 따르고, 서킷 브레이커는
 * 이 타입을 실패로 세지 않는다 — PG의 건강이 아니라 우리 요청량 문제다 (참고: 07_payment.md E.4).
 */
@Getter
public class PgRateLimitedException extends PgNotProcessedException {

    @Nullable
    private final Duration retryAfter;

    public PgRateLimitedException(String customMessage, @Nullable Duration retryAfter) {
        super(ErrorType.PG_RATE_LIMITED, customMessage);
        this.retryAfter = retryAfter;
    }
}
