package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * 같은 키로 접수된 주문을 찾는다. 있는 것도 없는 것도 정상이라 예외 대신 Optional로 돌려준다.
     * 없을 때 무엇을 할지는 호출자가 정한다.
     */
    @Transactional(readOnly = true)
    public Optional<Order> findByIdempotencyKey(OrderServiceDto.FindByIdempotencyKeyCommand command) {
        return orderRepository.findByIdempotencyKey(command.idempotencyKey());
    }

    /**
     * 결제가 주문을 가리킬 때 쓰는 조회 경로. API가 주고받는 주문 식별자는 orderNumber뿐이다 (참고: Order-011).
     */
    @Transactional(readOnly = true)
    public Order findByOrderNumber(OrderServiceDto.FindByOrderNumberCommand command) {
        return orderRepository.findByOrderNumber(command.orderNumber())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    /**
     * 주문번호를 채번해 접수 상태로 저장한다. 채번은 접수 시점에 한다 (참고: Order-011).
     */
    @Transactional
    public Order draft(OrderServiceDto.DraftCommand command) {
        Order order = Order.draft(command.memberId(), OrderNumber.generate(), command.idempotencyKey());

        return orderRepository.save(order);
    }

    @Transactional
    public Order confirm(OrderServiceDto.ConfirmCommand command) {
        Order order = findExistingOrder(command.orderId());
        order.confirm(command.lines());

        return order;
    }

    @Transactional
    public Order markOrderFailed(OrderServiceDto.MarkOrderFailedCommand command) {
        Order order = findExistingOrder(command.orderId());
        order.markOrderFailed();

        return order;
    }

    /**
     * 결제 완료 처리. 이 전이를 트리거하는 곳은 결제 흐름뿐이다 (참고: 07_payment.md Payment-004).
     */
    @Transactional
    public Order pay(OrderServiceDto.PayCommand command) {
        Order order = findExistingOrder(command.orderId());
        order.pay();

        return order;
    }

    /**
     * 결제 실패 처리. <b>재고 복원은 여기서 하지 않는다</b> — 차감한 곳이 되돌린다 (참고: Order-007, Payment-006).
     */
    @Transactional
    public Order markPaymentFailed(OrderServiceDto.MarkPaymentFailedCommand command) {
        Order order = findExistingOrder(command.orderId());
        order.markPaymentFailed();

        return order;
    }

    private Order findExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }
}
