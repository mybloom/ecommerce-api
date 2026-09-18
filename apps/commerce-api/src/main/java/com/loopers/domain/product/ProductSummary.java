package com.loopers.domain.product;

import com.loopers.domain.shared.Money;

/**
 * 상품 목록에서 상품 하나를 보여주는 조회 전용 정보. 엔티티가 아니다 (참고: Product-005).
 */
public record ProductSummary(
        Long productId,
        String name,
        String representativeImage,
        Long brandId,
        String brandName,
        Money price,
        int likeCount,
        StockQuantity stockQuantity
) {
    public boolean isSoldOut() {
        return stockQuantity.isSoldOut();
    }
}
