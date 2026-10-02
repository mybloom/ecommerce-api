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
public class PgNotProcessedException extends CoreException {

    public PgNotProcessedException(String customMessage) {
        super(ErrorType.BAD_GATEWAY, customMessage);
    }
}
