package com.loopers.domain.productlike;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLikeServiceTest {

    private ProductLikeService productLikeService;

    @Mock
    private ProductLikeRepository productLikeRepository;

    @BeforeEach
    void setUp() {
        productLikeService = new ProductLikeService(productLikeRepository);
    }

    @Nested
    @DisplayName("like")
    class Like {

        @Test
        @DisplayName("좋아요가 없으면 회원·상품 식별자를 담아 저장한다")
        void savesProductLike_whenNotLikedYet() {
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long productId = ProductLikeFixture.DEFAULT_PRODUCT_ID;
            when(productLikeRepository.existsByMemberIdAndProductId(memberId, productId)).thenReturn(false);

            ProductLikeServiceDto.LikeQuery query =
                    productLikeService.like(new ProductLikeServiceDto.LikeCommand(memberId, productId));

            ArgumentCaptor<ProductLike> savedLikeCaptor = ArgumentCaptor.forClass(ProductLike.class);
            verify(productLikeRepository, times(1)).save(savedLikeCaptor.capture());
            assertAll(
                    () -> assertThat(query.memberId()).isEqualTo(memberId),
                    () -> assertThat(query.productId()).isEqualTo(productId),
                    () -> assertThat(query.duplicated()).isFalse(),
                    () -> assertThat(savedLikeCaptor.getValue().getMemberId()).isEqualTo(memberId),
                    () -> assertThat(savedLikeCaptor.getValue().getProductId()).isEqualTo(productId)
            );
        }

        @Test
        @DisplayName("이미 좋아요한 상태면 저장하지 않고 중복으로 표시한다")
        void marksAsDuplicated_whenAlreadyLiked() {
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long productId = ProductLikeFixture.DEFAULT_PRODUCT_ID;
            when(productLikeRepository.existsByMemberIdAndProductId(memberId, productId)).thenReturn(true);

            ProductLikeServiceDto.LikeQuery query =
                    productLikeService.like(new ProductLikeServiceDto.LikeCommand(memberId, productId));

            assertThat(query.duplicated()).isTrue();
            verify(productLikeRepository, never()).save(any(ProductLike.class));
        }
    }

    @Nested
    @DisplayName("unlike")
    class Unlike {

        @Test
        @DisplayName("좋아요가 있으면 삭제한다")
        void deletesProductLike_whenLiked() {
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long productId = ProductLikeFixture.DEFAULT_PRODUCT_ID;
            ProductLike productLike = ProductLikeFixture.aProductLikeOf(memberId, productId);
            when(productLikeRepository.findByMemberIdAndProductId(memberId, productId))
                    .thenReturn(Optional.of(productLike));

            ProductLikeServiceDto.UnlikeQuery query =
                    productLikeService.unlike(new ProductLikeServiceDto.UnlikeCommand(memberId, productId));

            assertAll(
                    () -> assertThat(query.memberId()).isEqualTo(memberId),
                    () -> assertThat(query.productId()).isEqualTo(productId),
                    () -> assertThat(query.duplicated()).isFalse()
            );
            verify(productLikeRepository, times(1)).delete(productLike);
        }

        @Test
        @DisplayName("좋아요가 없으면 삭제하지 않고 중복으로 표시한다")
        void marksAsDuplicated_whenNotLiked() {
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long productId = ProductLikeFixture.DEFAULT_PRODUCT_ID;
            when(productLikeRepository.findByMemberIdAndProductId(memberId, productId))
                    .thenReturn(Optional.empty());

            ProductLikeServiceDto.UnlikeQuery query =
                    productLikeService.unlike(new ProductLikeServiceDto.UnlikeCommand(memberId, productId));

            assertThat(query.duplicated()).isTrue();
            verify(productLikeRepository, never()).delete(any(ProductLike.class));
        }
    }

    @Nested
    @DisplayName("retrieveLikedProductIds")
    class RetrieveLikedProductIds {

        @Test
        @DisplayName("productIds가 비어 있으면 Repository를 조회하지 않고 빈 집합을 반환한다")
        void returnsEmptySetWithoutQuery_whenProductIdsAreEmpty() {
            // given
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            List<Long> emptyProductIds = List.of();

            // when
            Set<Long> likedProductIds = productLikeService.retrieveLikedProductIds(
                    new ProductLikeServiceDto.RetrieveLikedProductIdsCommand(memberId, emptyProductIds));

            // then
            assertThat(likedProductIds).isEmpty();
            verify(productLikeRepository, never()).findLikedProductIds(any(), anyList());
        }

        @Test
        @DisplayName("productIds가 있으면 회원이 좋아요한 id만 조회해 반환한다")
        void returnsLikedIds_whenProductIdsAreGiven() {
            // given
            Long memberId = ProductLikeFixture.DEFAULT_MEMBER_ID;
            Long likedProductId = 11L;
            Long notLikedProductId = 22L;
            List<Long> productIds = List.of(likedProductId, notLikedProductId);
            when(productLikeRepository.findLikedProductIds(memberId, productIds))
                    .thenReturn(Set.of(likedProductId));

            // when
            Set<Long> likedProductIds = productLikeService.retrieveLikedProductIds(
                    new ProductLikeServiceDto.RetrieveLikedProductIdsCommand(memberId, productIds));

            // then
            assertThat(likedProductIds).containsExactly(likedProductId);
        }
    }
}
