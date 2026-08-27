package com.loopers.domain.order;

import com.loopers.domain.product.Product;

/**
 * 3단계(Domain Layer)에서 채운다. 주문에 담긴 상품 한 종류이며 Order 애그리거트 내부다 (참고: Order-008).
 */
public class OrderLine {

    /**
     * 상품명과 단가를 이 시점에 복사해 보관한다 (참고: Order-001).
     */
    public static OrderLine of(Product product, int quantity) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }
}
