package com.loopers.application.order;

import com.loopers.domain.order.IdempotencyKey;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderServiceDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
@Component
public class OrderUseCase {

    private final OrderService orderService;
    private final OrderProcessor orderProcessor;

    /**
     * 주문을 접수하고 재고를 확보해 결제 가능한 상태로 만든다 (참고: 06_order.md UC-1).
     * <p>
     * 접수(T1)와 확정(T2)이 서로 다른 트랜잭션이므로 여기에는 트랜잭션을 걸지 않는다 (참고: Order-004).
     * <p>
     * 선조회 없이 곧바로 접수를 시도한다. <b>orderNumber는 충돌하지 않는다고 전제</b>하므로
     * 제약 위반의 원인은 idempotencyKey 하나뿐이고, 그 위반이 곧 "이미 접수된 요청"이라는 신호다.
     * 중복 요청이면 재조회해서 그 주문의 현재 상태를 그대로 돌려준다 (참고: Order-004).
     */
    public OrderUseCaseDto.PlaceOrderResult place(OrderUseCaseDto.PlaceOrderInfo info) {
        IdempotencyKey idempotencyKey = IdempotencyKey.of(info.idempotencyKey());

        Order drafted;
        try {
            drafted = orderProcessor.accept(
                    new OrderServiceDto.DraftCommand(info.memberId(), idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            return OrderUseCaseDto.PlaceOrderResult.duplicated(findConcurrentlyAcceptedOrder(idempotencyKey));
        }

        final Order confirmed = confirm(drafted, info);

        return OrderUseCaseDto.PlaceOrderResult.from(confirmed);
    }

    /**
     * 제약 위반 직후의 재조회. 위반이 idempotencyKey 때문이었다면 상대가 커밋한 주문이 잡힌다.
     * 못 찾으면 전제(orderNumber는 충돌하지 않는다)가 깨진 것이므로 그대로 실패시킨다.
     */
    private Order findConcurrentlyAcceptedOrder(IdempotencyKey idempotencyKey) {
        return orderService.findByIdempotencyKey(
                        new OrderServiceDto.FindByIdempotencyKeyCommand(idempotencyKey))
                .orElseThrow(() -> new CoreException(ErrorType.CONFLICT,
                        "주문 접수에 실패했습니다. 잠시 후 다시 시도해주세요."));
    }

    /**
     * T2가 실패하면 T3로 주문만 실패로 남기고, 발생한 예외는 그대로 전파한다.
     * 재고는 T2 롤백으로 함께 되돌아가므로 복원하지 않는다. <b>여기서 잡은 예외는 재시도하지 않는다.</b>
     */
    private Order confirm(Order drafted, OrderUseCaseDto.PlaceOrderInfo info) {
        try {
            return orderProcessor.confirm(drafted.getId(), info.items());
        } catch (RuntimeException e) {
            markOrderFailedPreservingCause(drafted.getId(), e);
            throw e;
        }
    }

    /**
     * T3가 실패해도 <b>원래 예외를 덮지 않는다.</b> 실패는 suppressed로 매달아 함께 올린다.
     * 덮이면 주문이 왜 확정되지 못했는지가 사라지고, T3 롤백으로 DB에도 흔적이 남지 않는다.
     */
    private void markOrderFailedPreservingCause(Long orderId, RuntimeException cause) {
        try {
            orderProcessor.markOrderFailed(orderId);
        } catch (RuntimeException compensationFailure) {
            cause.addSuppressed(compensationFailure);
        }
    }
}
