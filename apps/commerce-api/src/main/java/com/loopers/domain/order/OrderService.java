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

    private Order findExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }
}
