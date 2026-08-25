package com.loopers.domain.productlike;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;

@Getter
@Entity
@Table(
        name = "product_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_like_member_product",
                columnNames = {"member_id", "product_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private ZonedDateTime likedAt;

    public static ProductLike like(Long memberId, Long productId) {
        ProductLike productLike = new ProductLike();

        productLike.memberId = requireNonNull(memberId);
        productLike.productId = requireNonNull(productId);
        productLike.likedAt = ZonedDateTime.now();

        return productLike;
    }
}
