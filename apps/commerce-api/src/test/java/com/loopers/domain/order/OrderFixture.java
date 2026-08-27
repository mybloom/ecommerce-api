package com.loopers.domain.order;

import com.loopers.domain.product.ProductFixture;

import java.util.List;

public class OrderFixture {

    public static final Long DEFAULT_MEMBER_ID = 1L;
    public static final OrderNumber DEFAULT_ORDER_NUMBER = OrderNumber.of("20260827-A3F9K2QP");
    public static final IdempotencyKey DEFAULT_IDEMPOTENCY_KEY = IdempotencyKey.of("3f8a1c2e-0001");
    public static final Long DEFAULT_PRODUCT_ID = 7L;
    public static final int DEFAULT_QUANTITY = 2;

    public static Order aDraftedOrder() {
        return Order.draft(DEFAULT_MEMBER_ID, DEFAULT_ORDER_NUMBER, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static Order aDraftedOrderOf(Long memberId) {
        return Order.draft(memberId, DEFAULT_ORDER_NUMBER, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static Order aConfirmedOrder() {
        Order order = aDraftedOrder();

        order.confirm(aLines());
        return order;
    }

    public static Order aPaidOrder() {
        Order order = aConfirmedOrder();

        order.pay();
        return order;
    }

    public static Order aFailedOrder() {
        Order order = aDraftedOrder();

        order.markOrderFailed();
        return order;
    }

    public static List<OrderLine> aLines() {
        return List.of(OrderLine.of(ProductFixture.aSavedProduct(DEFAULT_PRODUCT_ID), DEFAULT_QUANTITY));
    }
}
