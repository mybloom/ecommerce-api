package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * PG가 요청을 처리하지 않았음이 확실하다 — 닿지 못했거나(연결 불가·connect timeout·DNS)
 * 수신을 거부했다(429·503).
 * <p>
 * <b>재시도가 안전한 유일한 타입이다.</b> 거래가 없으므로 다시 보내도 이중 승인이 되지 않는다
 * (참고: Payment-010).
 */
public abstract sealed class PgNotProcessedException extends CoreException
        permits PgConnectionFailedException, PgHostUnresolvedException, PgRateLimitedException, PgUnavailableException {

    /**
     * 원인별 하위 타입이 각자의 에러 코드를 정한다. 재시도 정책이 같은 단위로 나뉜다 (참고: 07_payment.md E.4)
     */
    protected PgNotProcessedException(ErrorType errorType, String customMessage) {
        super(errorType, customMessage);
    }
}
