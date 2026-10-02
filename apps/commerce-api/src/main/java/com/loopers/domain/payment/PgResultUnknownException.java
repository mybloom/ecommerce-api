package com.loopers.domain.payment;

/**
 * 요청은 보냈으나 응답을 받지 못해 <b>PG가 처리했는지 알 수 없다</b> (read timeout).
 * <p>
 * <b>CoreException을 상속하지 않는 것이 이 타입의 핵심이다.</b> 보상은 {@code catch (CoreException)}에
 * 걸려 있으므로, 이 예외는 자동으로 보상을 건너뛴다 — "결제를 종결하지 않는다"가 정책이 아니라
 * 타입으로 강제된다. 실패로 확정하면 PG가 승인한 결제를 되돌릴 수 없게 만든다 (참고: Payment-010).
 */
public class PgResultUnknownException extends RuntimeException {

    public PgResultUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
