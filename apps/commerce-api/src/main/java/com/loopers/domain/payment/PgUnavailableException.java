package com.loopers.domain.payment;

import com.loopers.support.error.ErrorType;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * PG가 503으로 지금은 요청을 받을 수 없다고 알렸다 (과부하·점검).
 * <p>
 * {@code Retry-After}는 없을 수 있다. 있으면 재시도가 그 시간을 따른다 (참고: 07_payment.md E.4).
 */
@Getter
public class PgUnavailableException extends PgNotProcessedException {

    @Nullable
    private final Duration retryAfter;

    public PgUnavailableException(String customMessage, @Nullable Duration retryAfter) {
        super(ErrorType.PG_UNAVAILABLE, customMessage);
        this.retryAfter = retryAfter;
    }
}
