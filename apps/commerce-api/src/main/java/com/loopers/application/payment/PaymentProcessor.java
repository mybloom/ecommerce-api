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
 * 수단과 무관한 접수(T0)와 실패 처리(T2)를 각각 독립 트랜잭션으로 수행한다.
 * 승인(T1)은 수단마다 달라 PaymentStrategy 구현이 가진다.
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
     * PG가 알려온 결과로 결제와 주문을 종결한다. <b>조회·판정·적용을 한 트랜잭션에 둔다</b> —
     * 나누면 두 콜백이 나란히 종결 판정을 통과해 같은 결제를 두 번 건드릴 수 있다 (참고: UC-2).
     */
    @Transactional
    public void settleByCallback(PaymentUseCaseDto.PgCallbackInfo info) {
        Order order = findOrder(info.orderId());
        Payment payment = paymentService.findByOrderId(
                new PaymentServiceDto.FindByOrderIdCommand(order.getId()));

        // 종결된 결제에 두 번째 결과를 적용하지 않는다. 재고가 두 번 복원되는 것을 막는다 (참고: Payment-005)
        if (payment.isFinalized() || info.status() == PaymentUseCaseDto.PgTransactionStatus.PENDING) {
            return;
        }

        boolean amountMatches = order.getTotalAmount().getAmount().equals(info.amount());

        if (info.status() == PaymentUseCaseDto.PgTransactionStatus.SUCCESS && amountMatches) {
            paymentService.approve(
                    new PaymentServiceDto.ApproveCommand(payment.getId(), info.transactionKey()));
            orderService.pay(new OrderServiceDto.PayCommand(order.getId()));
            return;
        }

        paymentService.fail(
                new PaymentServiceDto.FailCommand(payment.getId(), failureReasonOf(info, amountMatches)));
        restoreStock(order);
        orderService.markPaymentFailed(new OrderServiceDto.MarkPaymentFailedCommand(order.getId()));
    }

    /**
     * PG가 보낸 사유는 사용자가 행동할 수 있는 정보라 그대로 남긴다. 다만 <b>금액이 어긋난 경우는
     * PG가 성공이라 했으므로 그쪽에 사유가 없어</b> 우리 문구를 쓴다 (참고: Payment-009).
     */
    private String failureReasonOf(PaymentUseCaseDto.PgCallbackInfo info, boolean amountMatches) {
        if (!amountMatches) {
            return "승인 금액이 주문 금액과 일치하지 않습니다.";
        }

        return (info.reason() == null || info.reason().isBlank())
                ? "PG 승인이 거절되었습니다."
                : info.reason();
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
