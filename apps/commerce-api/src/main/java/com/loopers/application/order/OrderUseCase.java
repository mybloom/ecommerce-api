package com.loopers.application.order;

import org.springframework.stereotype.Component;

@Component
public class OrderUseCase {

    /**
     * 주문을 접수하고 재고를 확보해 결제 가능한 상태로 만든다 (참고: 06_order.md UC-1).
     * 접수(T1)와 확정(T2)은 서로 다른 트랜잭션이다 (참고: Order-004).
     */
    public OrderUseCaseDto.PlaceOrderResult place(OrderUseCaseDto.PlaceOrderInfo info) {
        throw new UnsupportedOperationException("2단계에서 구현");
    }
}
