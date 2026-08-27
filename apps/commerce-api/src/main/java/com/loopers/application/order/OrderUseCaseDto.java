package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderUseCaseDto {

    public record OrderItemInfo(Long productId, int quantity) {
    }

    public record PlaceOrderInfo(Long memberId, String idempotencyKey, List<OrderItemInfo> items) {
    }

    /**
     * 내부 식별자 id는 담지 않는다 (참고: Order-011).
     * 주문 라인 상세도 담지 않는다 — 필요하면 UC-3으로 다시 받는다.
     */
    public record PlaceOrderResult(
            String orderNumber,
            OrderStatus status,
            Long totalAmount,
            ZonedDateTime orderedAt,
            boolean isDuplicatedRequest
    ) {
        /**
         * 이번 요청으로 새로 접수·확정된 주문.
         */
        public static PlaceOrderResult from(Order order) {
            return of(order, false);
        }

        /**
         * 같은 Idempotency-Key로 이미 접수돼 있어 그대로 돌려주는 주문 (참고: Order-004).
         */
        public static PlaceOrderResult duplicated(Order order) {
            return of(order, true);
        }

        private static PlaceOrderResult of(Order order, boolean isDuplicatedRequest) {
            return new PlaceOrderResult(
                    order.getOrderNumber().getValue(),
                    order.getStatus(),
                    order.getTotalAmount().getAmount(),
                    order.getOrderedAt(),
                    isDuplicatedRequest
            );
        }
    }
}
