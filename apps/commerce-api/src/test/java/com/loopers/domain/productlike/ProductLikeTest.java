package com.loopers.domain.productlike;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductLikeTest {

    @Nested
    @DisplayName("like")
    class Like {

        @Test
        @DisplayName("회원식별자, 상품 식별자, 생성 시점을 담아 생성한다")
        void createsProductLike_withMemberIdProductIdAndLikedAt() {
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long productId = ProductLikeFixture.DEFAULT_PRODUCT_ID;
            ZonedDateTime beforeCreation = ZonedDateTime.now();

            ProductLike productLike = ProductLike.like(memberId, productId);

            assertAll(
                    () -> assertThat(productLike.getMemberId()).isEqualTo(memberId),
                    () -> assertThat(productLike.getProductId()).isEqualTo(productId),
                    () -> assertThat(productLike.getLikedAt()).isAfterOrEqualTo(beforeCreation)
            );
        }
    }
}
