package com.loopers.domain.payment;

import com.loopers.support.error.ErrorType;

/**
 * PG 주소를 찾지 못해 연결을 시작하지도 못했다 (DNS 실패).
 * <p>
 * 요청이 나가지 않은 것은 연결 실패와 같지만 <b>재시도하지 않는다.</b> 대개 설정 오류이고, JVM이 실패한
 * 조회를 잠시 기억해 바로 다시 해도 같은 결과다. 사용자에게는 연결 실패와 같은 에러 코드를 준다
 * (참고: 07_payment.md E.3·E.4).
 */
public final class PgHostUnresolvedException extends PgNotProcessedException {

    public PgHostUnresolvedException(String customMessage) {
        super(ErrorType.PG_CONNECTION_FAILED, customMessage);
    }
}
