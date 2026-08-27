package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentUseCase {

    private final PaymentProcessor paymentProcessor;

    /**
     * 결제 대기 주문의 금액을 받아내고 주문을 종결시킨다 (참고: 07_payment.md UC-1).
     * <p>
     * 접수(T0)·승인(T1)·실패 처리(T2)가 서로 다른 트랜잭션이므로 여기에는 트랜잭션을 걸지 않는다.
     * T0의 제약 위반은 커밋 시점에 터지므로 트랜잭션 <b>밖</b>인 여기서만 잡을 수 있다.
     */
    public PaymentUseCaseDto.PayResult pay(PaymentUseCaseDto.PayInfo info) {
        Payment accepted;
        try {
            accepted = paymentProcessor.accept(info);
        } catch (DataIntegrityViolationException e) {
            // orderId UNIQUE 위반. 이미 결제가 붙은 주문이라는 뜻이다 (참고: Payment-001)
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행된 주문입니다.");
        }

        final Payment payment = approve(accepted, info);
        return PaymentUseCaseDto.PayResult.from(info.orderNumber(), payment);
    }

    /**
     * T1이 실패하면 T2로 결제를 실패로 남기고 재고를 복원한 뒤, 발생한 예외는 그대로 전파한다.
     * <b>여기서 잡은 예외는 재시도하지 않는다.</b>
     * <p>
     * <b>보상은 CoreException에만 건다.</b> 그것만이 커밋 전에 결정되는 도메인 실패라 T1이 커밋되지 않았음이
     * 확실하기 때문이다. 락 타임아웃이나 커밋 실패는 포인트가 빠졌는지조차 알 수 없어, 그 상태에서 재고를
     * 되돌리면 근거 없는 보상이 된다. 그런 실패는 결제를 PENDING으로 남긴 채 전파한다 (참고: C.2).
     */
    private Payment approve(Payment accepted, PaymentUseCaseDto.PayInfo info) {
        try {
            return paymentProcessor.approve(accepted, info.memberId());
        } catch (CoreException e) {
            paymentProcessor.markFailed(accepted, info.orderNumber(), e.getMessage());
            throw e;
        }
    }
}
