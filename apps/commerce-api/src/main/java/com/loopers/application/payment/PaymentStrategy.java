package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentMethod;

/**
 * 결제 수단마다 다른 승인 한 단계만 책임진다. 접수와 실패 보상은 수단과 무관해 들어오지 않는다
 * (참고: Payment-006).
 */
public interface PaymentStrategy {

    PaymentMethod method();

    /**
     * <b>호출자는 이 메서드를 트랜잭션으로 감싸면 안 된다.</b> PG를 거치는 구현은 외부 왕복 동안
     * 커넥션을 쥐면 안 되고, 접수가 커밋된 뒤라야 먼저 도착한 콜백이 볼 것이 있다 (참고: Payment-005).
     */
    Payment approve(Payment accepted, PaymentUseCaseDto.PayInfo info);
}
