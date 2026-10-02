package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * PG가 요청을 받아 처리했고 거절했다 (4xx·5xx).
 * <p>
 * 거래는 생기지 않았으므로 결제를 실패로 확정해도 안전하지만, <b>재시도하지 않는다</b> —
 * 같은 요청이라 같은 답이고, 거래를 만든 뒤 5xx를 주는 PG라면 이중 승인이 된다 (참고: Payment-010).
 */
public class PgRejectedException extends CoreException {

    public PgRejectedException(String customMessage) {
        super(ErrorType.BAD_GATEWAY, customMessage);
    }
}
