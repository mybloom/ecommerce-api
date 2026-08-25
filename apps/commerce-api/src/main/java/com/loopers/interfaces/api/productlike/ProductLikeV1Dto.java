package com.loopers.interfaces.api.productlike;

import com.loopers.application.productlike.ProductLikeUseCaseDto;

public class ProductLikeV1Dto {

    public record LikeResponse(
            Long memberId,
            Long productId,
            boolean isDuplicatedRequest
    ) {
        public static LikeResponse from(ProductLikeUseCaseDto.LikeResult result) {
            return new LikeResponse(
                    result.memberId(),
                    result.productId(),
                    result.isDuplicatedRequest()
            );
        }
    }

    public record UnlikeResponse(
            Long memberId,
            Long productId,
            boolean isDuplicatedRequest
    ) {
        public static UnlikeResponse from(ProductLikeUseCaseDto.UnlikeResult result) {
            return new UnlikeResponse(
                    result.memberId(),
                    result.productId(),
                    result.isDuplicatedRequest()
            );
        }
    }
}
