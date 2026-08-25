package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLike, Long> {
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<ProductLike> findByMemberIdAndProductId(Long memberId, Long productId);
}
