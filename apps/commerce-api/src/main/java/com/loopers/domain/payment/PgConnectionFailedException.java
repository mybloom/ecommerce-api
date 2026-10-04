package com.loopers.domain.payment;

import com.loopers.support.error.ErrorType;

/**
 * PG와 연결을 맺지 못해 요청이 나가지 않았다 — 연결 거부, connect timeout.
 * <p>
 * 두 원인을 한 타입으로 묶는 것은 재시도 정책(짧은 지터 후 1회)이 같기 때문이다 (참고: 07_payment.md E.4).
 */
public final class PgConnectionFailedException extends PgNotProcessedException {

    public PgConnectionFailedException(String customMessage) {
        super(ErrorType.PG_CONNECTION_FAILED, customMessage);
    }
}
