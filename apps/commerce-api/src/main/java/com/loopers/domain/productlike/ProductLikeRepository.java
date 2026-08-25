package com.loopers.domain.productlike;

import java.util.Optional;

public interface ProductLikeRepository {
    ProductLike save(ProductLike productLike);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<ProductLike> findByMemberIdAndProductId(Long memberId, Long productId);

    void delete(ProductLike productLike);
}
