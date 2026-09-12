package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderNumber;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderServiceDto;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentServiceDto;
import com.loopers.domain.point.PointService;
import com.loopers.domain.point.PointServiceDto;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductServiceDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.Nullable;

/**
 * 접수(T0)·승인(T1)·실패 처리(T2)를 각각 독립 트랜잭션으로 수행한다.
 * UseCase에 트랜잭션이 없으므로 이 컴포넌트의 메서드 하나하나가 곧 트랜잭션 경계다.
 * <p>
 * 접수를 별도 트랜잭션으로 올린 이유는 두 가지다. 승인(T1)이 롤백되면 그 안에서 만든 Payment 행도 함께
 * 사라져 실패를 기록할 대상이 없어지고, <b>커밋된 행이 없으면 orderId UNIQUE로 동시 결제를 막을 수도 없다</b>
 * (참고: 07_payment.md Payment-001).
 */
@RequiredArgsConstructor
@Component
public class PaymentProcessor {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PointService pointService;
    private final ProductService productService;

    /**
     * T0. 접수 — 커밋된다. 결제 금액은 요청이 아니라 주문에서 가져온다 (참고: Payment-002).
     */
    @Transactional
    public Payment accept(PaymentUseCaseDto.PayInfo info) {
        Order order = findPayableOrder(info.orderNumber(), info.memberId());

        return paymentService.request(new PaymentServiceDto.RequestCommand(
                order.getId(), info.memberId(),
                PaymentMethod.valueOf(info.method().name()),
                order.getTotalAmount()));
    }

    /**
     * T1. 승인 — 커밋된다. 포인트 차감이 이 안에 있으므로 실패하면 차감분도 함께 롤백된다.
     */
    @Transactional
    public Payment approve(Payment accepted, Long memberId) {
        pointService.use(new PointServiceDto.UseCommand(memberId, accepted.getAmount()));

        // POINT 결제에는 PG 거래 식별자가 없다 (참고: 07_payment.md B.1)
        Payment approved = paymentService.approve(new PaymentServiceDto.ApproveCommand(accepted.getId(), null));
        orderService.pay(new OrderServiceDto.PayCommand(accepted.getOrderId()));

        return approved;
    }

    /**
     * T2. 실패 처리 — T1이 실패했을 때만 수행하며, 별도로 커밋된다.
     * 포인트는 T1 롤백으로 되돌아오지만 <b>재고는 주문 확정 트랜잭션에서 빠진 것이라</b>
     * 여기서 명시적으로 복원한다 (참고: Order-007).
     */
    @Transactional
    public Payment markFailed(Payment accepted, String orderNumber, @Nullable String reason) {
        Order order = findOrder(orderNumber);

        Payment failed = paymentService.fail(new PaymentServiceDto.FailCommand(accepted.getId(), reason));
        restoreStock(order);
        orderService.markPaymentFailed(new OrderServiceDto.MarkPaymentFailedCommand(order.getId()));

        return failed;
    }

    /**
     * 남의 주문은 존재 자체를 노출하지 않으므로 403이 아니라 404다 (참고: 06_order.md UC-3).
     */
    private Order findPayableOrder(String orderNumber, Long memberId) {
        Order order = findOrder(orderNumber);

        if (!order.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        if (!order.isPayable()) {
            throw new CoreException(ErrorType.CONFLICT, "결제할 수 없는 주문입니다.");
        }

        return order;
    }

    private Order findOrder(String orderNumber) {
        return orderService.findByOrderNumber(
                new OrderServiceDto.FindByOrderNumberCommand(OrderNumber.of(orderNumber)));
    }

    /**
     * 복원 수량의 근거는 항상 OrderLine.quantity다. 다른 근거를 두지 않는다 (참고: Order-007).
     */
    private void restoreStock(Order order) {
        for (OrderLine line : order.getLines()) {
            Product product = productService.retrieveForRestore(
                    new ProductServiceDto.RetrieveCommand(line.getProductId()));
            product.increaseStock(line.getQuantity());
        }
    }
}
