package com.loopers.domain.payment;

import com.loopers.support.error.ErrorType;

/**
 * PG 서킷이 열려 있어 요청을 보내지 않았다. 최근 PG 실패가 임계치를 넘었다는 뜻이다.
 * <p>
 * 요청이 나가지 않아 거래가 없음이 확실하므로 다른 "처리 안 됨"과 똑같이 실패로 확정된다.
 * 열림 유지 시간 동안 같은 거절이 나므로 재시도하지 않는다 (참고: 07_payment.md F.2).
 */
public final class PgCircuitOpenException extends PgNotProcessedException {

    public PgCircuitOpenException(String customMessage) {
        super(ErrorType.PG_CIRCUIT_OPEN, customMessage);
    }
}
