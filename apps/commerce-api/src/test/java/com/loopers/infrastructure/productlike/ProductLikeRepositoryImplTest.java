package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeFixture;
import com.loopers.domain.productlike.ProductLikeRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductLikeRepositoryImplTest {

    private final ProductLikeRepository productLikeRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductLikeRepositoryImplTest(ProductLikeRepository productLikeRepository, DatabaseCleanUp databaseCleanUp) {
        this.productLikeRepository = productLikeRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("findLikedProductIds")
    class FindLikedProductIds {

        @Test
        @DisplayName("주어진 상품 중 그 회원이 좋아요한 id만 반환한다")
        void returnsOnlyLikedIdsAmongGivenProducts() {
            // given
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long likedProductId = 11L;
            Long notLikedProductId = 22L;
            productLikeRepository.save(ProductLike.like(memberId, likedProductId));

            List<Long> productIds = List.of(likedProductId, notLikedProductId);

            // when
            Set<Long> likedProductIds = productLikeRepository.findLikedProductIds(memberId, productIds);

            // then
            assertThat(likedProductIds).containsExactly(likedProductId);
        }

        @Test
        @DisplayName("다른 회원이 누른 좋아요는 반환하지 않는다")
        void excludesOtherMembersLikes() {
            // given
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long otherMemberId = memberId + 1;
            Long productLikedByOther = 33L;
            productLikeRepository.save(ProductLike.like(otherMemberId, productLikedByOther));

            List<Long> productIds = List.of(productLikedByOther);

            // when
            Set<Long> likedProductIds = productLikeRepository.findLikedProductIds(memberId, productIds);

            // then
            assertThat(likedProductIds).isEmpty();
        }

        @Test
        @DisplayName("좋아요했지만 주어진 상품 목록에 없는 id는 반환하지 않는다")
        void excludesLikesOutsideGivenProducts() {
            // given
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long likedProductIdInPage = 11L;
            Long likedProductIdOutOfPage = 99L;
            productLikeRepository.save(ProductLike.like(memberId, likedProductIdInPage));
            productLikeRepository.save(ProductLike.like(memberId, likedProductIdOutOfPage));

            List<Long> productIdsInPage = List.of(likedProductIdInPage);

            // when
            Set<Long> likedProductIds = productLikeRepository.findLikedProductIds(memberId, productIdsInPage);

            // then
            assertThat(likedProductIds).containsExactly(likedProductIdInPage);
        }
    }
}
