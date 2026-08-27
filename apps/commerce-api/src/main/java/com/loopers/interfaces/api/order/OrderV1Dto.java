package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderUseCaseDto;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    /**
     * 주문 금액도 결제 수단도 요청에 포함하지 않는다 (참고: Order-002, 07_payment.md UC-1).
     */
    public record PlaceOrderRequest(
            @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.")
            @Valid
            List<OrderItemRequest> items
    ) {
        public OrderUseCaseDto.PlaceOrderInfo toInfo(Long memberId, String idempotencyKey) {
            return new OrderUseCaseDto.PlaceOrderInfo(
                    memberId,
                    idempotencyKey,
                    items.stream()
                            .map(item -> new OrderUseCaseDto.OrderItemInfo(item.productId(), item.quantity()))
                            .toList()
            );
        }
    }

    public record OrderItemRequest(
            @NotNull(message = "상품 식별자는 필수입니다.")
            Long productId,

            @NotNull(message = "주문 수량은 필수입니다.")
            @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다.")
            Integer quantity
    ) {
    }

    public record PlaceOrderResponse(
            String orderNumber,
            OrderStatus status,
            Long totalAmount,
            ZonedDateTime orderedAt
    ) {
        public static PlaceOrderResponse from(OrderUseCaseDto.PlaceOrderResult result) {
            return new PlaceOrderResponse(
                    result.orderNumber(),
                    result.status(),
                    result.totalAmount(),
                    result.orderedAt()
            );
        }
    }
}
