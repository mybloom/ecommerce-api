package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Repository
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public ProductLike save(ProductLike productLike) {
        return productLikeJpaRepository.save(productLike);
    }

    @Override
    public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
        return productLikeJpaRepository.existsByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public Optional<ProductLike> findByMemberIdAndProductId(Long memberId, Long productId) {
        return productLikeJpaRepository.findByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public Set<Long> findLikedProductIds(Long memberId, List<Long> productIds) {
        return Set.copyOf(productLikeJpaRepository.findLikedProductIds(memberId, productIds));
    }

    @Override
    public void delete(ProductLike productLike) {
        productLikeJpaRepository.delete(productLike);
    }
}
