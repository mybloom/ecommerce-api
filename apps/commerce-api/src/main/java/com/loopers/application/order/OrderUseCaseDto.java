package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderNumber;
import com.loopers.domain.order.OrderServiceDto;
import com.loopers.domain.payment.Payment;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderUseCaseDto {

    /**
     * API 응답 계약이 도메인 enum 에 묶이지 않도록 레이어마다 따로 둔다.
     * 도메인에서 상수를 바꿔도 여기서 변환이 깨지며 드러난다.
     */
    public enum OrderStatus {
        PENDING, AWAITING_PAYMENT, ORDER_FAILED, PAID, PAYMENT_FAILED
    }

    public enum PaymentMethod {
        POINT, CARD
    }

    public enum PaymentStatus {
        PENDING, APPROVED, FAILED
    }

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
                    OrderStatus.valueOf(order.getStatus().name()),
                    order.getTotalAmount().getAmount(),
                    order.getOrderedAt(),
                    isDuplicatedRequest
            );
        }
    }

    public record GetOrderInfo(Long memberId, String orderNumber) {
        public OrderServiceDto.RetrieveCommand toCommand() {
            return new OrderServiceDto.RetrieveCommand(memberId, OrderNumber.of(orderNumber));
        }
    }

    /**
     * 내부 식별자 id 와 PG 거래 식별자는 담지 않는다 (참고: Order-011, 06_order.md UC-3).
     * 결제 요청 전인 주문은 payment 가 비어 있다.
     */
    public record GetOrderResult(
            String orderNumber,
            OrderStatus status,
            Long totalAmount,
            ZonedDateTime orderedAt,
            @Nullable ZonedDateTime paidAt,
            List<OrderLineResult> lines,
            @Nullable PaymentResult payment
    ) {
        public static GetOrderResult from(Order order, @Nullable Payment payment) {
            return new GetOrderResult(
                    order.getOrderNumber().getValue(),
                    OrderStatus.valueOf(order.getStatus().name()),
                    order.getTotalAmount().getAmount(),
                    order.getOrderedAt(),
                    order.getPaidAt(),
                    order.getLines().stream().map(OrderLineResult::from).toList(),
                    payment == null ? null : PaymentResult.from(payment)
            );
        }
    }

    public record OrderLineResult(
            Long productId,
            String productName,
            Long unitPrice,
            int quantity,
            Long lineAmount
    ) {
        public static OrderLineResult from(OrderLine line) {
            return new OrderLineResult(
                    line.getProductId(),
                    line.getProductName(),
                    line.getUnitPrice().getAmount(),
                    line.getQuantity(),
                    line.lineAmount().getAmount()
            );
        }
    }

    public record PaymentResult(
            PaymentMethod method,
            PaymentStatus status,
            @Nullable ZonedDateTime approvedAt,
            @Nullable String failureReason
    ) {
        public static PaymentResult from(Payment payment) {
            return new PaymentResult(
                    PaymentMethod.valueOf(payment.getMethod().name()),
                    PaymentStatus.valueOf(payment.getStatus().name()),
                    payment.getApprovedAt(),
                    payment.getFailureReason()
            );
        }
    }
}
