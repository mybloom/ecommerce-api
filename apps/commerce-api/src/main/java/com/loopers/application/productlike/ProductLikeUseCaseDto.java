package com.loopers.application.productlike;

import com.loopers.domain.productlike.ProductLikeServiceDto;

public class ProductLikeUseCaseDto {

    public record LikeInfo(Long memberId, Long productId) {
        public ProductLikeServiceDto.LikeCommand toCommand() {
            return new ProductLikeServiceDto.LikeCommand(memberId, productId);
        }
    }

    public record LikeResult(Long memberId, Long productId, boolean isDuplicatedRequest) {
        public static LikeResult from(ProductLikeServiceDto.LikeQuery query) {
            return new LikeResult(query.memberId(), query.productId(), query.duplicated());
        }

        public static LikeResult duplicated(Long memberId, Long productId) {
            return new LikeResult(memberId, productId, true);
        }
    }

    public record UnlikeInfo(Long memberId, Long productId) {
        public ProductLikeServiceDto.UnlikeCommand toCommand() {
            return new ProductLikeServiceDto.UnlikeCommand(memberId, productId);
        }
    }

    public record UnlikeResult(Long memberId, Long productId, boolean isDuplicatedRequest) {
        public static UnlikeResult from(ProductLikeServiceDto.UnlikeQuery query) {
            return new UnlikeResult(query.memberId(), query.productId(), query.duplicated());
        }
    }
}
