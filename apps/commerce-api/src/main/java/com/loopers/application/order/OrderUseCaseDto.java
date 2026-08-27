package com.loopers.application.order;

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
            ZonedDateTime orderedAt
    ) {
    }
}
