package com.loopers.domain.productlike;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductLikeRepository {
    ProductLike save(ProductLike productLike);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<ProductLike> findByMemberIdAndProductId(Long memberId, Long productId);

    Set<Long> findLikedProductIds(Long memberId, List<Long> productIds);

    void delete(ProductLike productLike);
}
