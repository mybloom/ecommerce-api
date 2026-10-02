package com.loopers.application.payment;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderServiceDto;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentServiceDto;
import com.loopers.domain.point.PointService;
import com.loopers.domain.point.PointServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PointPaymentStrategy implements PaymentStrategy {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PointService pointService;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.POINT;
    }

    /**
     * 포인트 차감이 이 트랜잭션 안에 있어 실패하면 함께 롤백된다. 재고는 다른 트랜잭션에서
     * 빠진 것이라 호출자가 복원한다 (참고: Order-007).
     */
    @Override
    @Transactional
    public Payment approve(Payment accepted, PaymentUseCaseDto.PayInfo info) {
        pointService.use(new PointServiceDto.UseCommand(info.memberId(), accepted.getAmount()));

        // POINT 결제에는 PG 거래 식별자가 없다 (참고: B.1)
        Payment approved = paymentService.approve(new PaymentServiceDto.ApproveCommand(accepted.getId(), null));
        orderService.pay(new OrderServiceDto.PayCommand(accepted.getOrderId()));

        return approved;
    }
}
