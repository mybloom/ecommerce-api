package com.loopers.domain.productlike;

import org.springframework.test.util.ReflectionTestUtils;

public class ProductLikeFixture {

    public static final Long DEFAULT_MEMBER_ID = 11L;
    public static final Long DEFAULT_PRODUCT_ID = 22L;

    public static ProductLike aProductLike() {
        return ProductLike.like(DEFAULT_MEMBER_ID, DEFAULT_PRODUCT_ID);
    }

    public static ProductLike aProductLikeOf(Long memberId, Long productId) {
        return ProductLike.like(memberId, productId);
    }

    public static ProductLike aSavedProductLike(Long id, Long memberId, Long productId) {
        ProductLike productLike = aProductLikeOf(memberId, productId);

        ReflectionTestUtils.setField(productLike, "id", id);
        return productLike;
    }
}
