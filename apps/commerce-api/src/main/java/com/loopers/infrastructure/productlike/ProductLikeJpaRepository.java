package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLike, Long> {
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<ProductLike> findByMemberIdAndProductId(Long memberId, Long productId);

    @Query("select pl.productId from ProductLike pl where pl.memberId = :memberId and pl.productId in :productIds")
    List<Long> findLikedProductIds(@Param("memberId") Long memberId, @Param("productIds") List<Long> productIds);
}
