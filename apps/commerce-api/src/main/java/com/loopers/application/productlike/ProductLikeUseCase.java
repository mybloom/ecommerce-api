package com.loopers.application.productlike;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeUseCase {

    private final ProductLikeProcessor productLikeProcessor;

    public ProductLikeUseCaseDto.LikeResult like(ProductLikeUseCaseDto.LikeInfo info) {
        try {
            return ProductLikeUseCaseDto.LikeResult.from(productLikeProcessor.like(info.toCommand()));
        } catch (DataIntegrityViolationException e) {
            return ProductLikeUseCaseDto.LikeResult.duplicated(info.memberId(), info.productId());
        }
    }

    public ProductLikeUseCaseDto.UnlikeResult unlike(ProductLikeUseCaseDto.UnlikeInfo info) {
        return ProductLikeUseCaseDto.UnlikeResult.from(productLikeProcessor.unlike(info.toCommand()));
    }
}
