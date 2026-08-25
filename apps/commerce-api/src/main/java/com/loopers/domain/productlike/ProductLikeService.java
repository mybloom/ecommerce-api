package com.loopers.domain.productlike;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
