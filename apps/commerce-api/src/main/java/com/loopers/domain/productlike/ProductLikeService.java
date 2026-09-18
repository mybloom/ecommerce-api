package com.loopers.domain.productlike;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@RequiredArgsConstructor
@Service
public class ProductLikeService {

    private final ProductLikeRepository productLikeRepository;

    @Transactional
    public ProductLikeServiceDto.LikeQuery like(ProductLikeServiceDto.LikeCommand command) {
        if (productLikeRepository.existsByMemberIdAndProductId(command.memberId(), command.productId())) {
            return new ProductLikeServiceDto.LikeQuery(command.memberId(), command.productId(), true);
        }

        productLikeRepository.save(ProductLike.like(command.memberId(), command.productId()));

        return new ProductLikeServiceDto.LikeQuery(command.memberId(), command.productId(), false);
    }

    /**
     * productIds 중 회원이 좋아요한 상품 id 만 돌려준다. 목록의 좋아요 여부 표시에 쓴다 (참고: Product-006).
     */
    @Transactional(readOnly = true)
    public Set<Long> retrieveLikedProductIds(ProductLikeServiceDto.RetrieveLikedProductIdsCommand command) {
        if (command.productIds().isEmpty()) {
            return Set.of();
        }

        return productLikeRepository.findLikedProductIds(command.memberId(), command.productIds());
    }

    @Transactional
    public ProductLikeServiceDto.UnlikeQuery unlike(ProductLikeServiceDto.UnlikeCommand command) {
        ProductLike productLike = productLikeRepository
                .findByMemberIdAndProductId(command.memberId(), command.productId())
                .orElse(null);

        if (productLike == null) {
            return new ProductLikeServiceDto.UnlikeQuery(command.memberId(), command.productId(), true);
        }

        productLikeRepository.delete(productLike);

        return new ProductLikeServiceDto.UnlikeQuery(command.memberId(), command.productId(), false);
    }
}
