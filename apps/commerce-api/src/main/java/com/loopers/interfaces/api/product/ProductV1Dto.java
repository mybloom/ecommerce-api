package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductUseCaseDto;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.Map;

public class ProductV1Dto {

    /**
     * sort 는 문자열로 받는다. 목록에 없는 값은 400 이 아니라 LATEST 로 처리하기 때문이다 (참고: Product-005).
     */
    public record GetProductsRequest(@Nullable Long memberId, @Nullable Long brandId, String sort, int page, int size) {
        private static final Map<String, ProductUseCaseDto.ProductSort> SORTS = Map.of(
                "latest", ProductUseCaseDto.ProductSort.LATEST,
                "priceDesc", ProductUseCaseDto.ProductSort.PRICE_DESC,
                "likeDesc", ProductUseCaseDto.ProductSort.LIKE_DESC
        );

        public ProductUseCaseDto.GetProductsInfo toInfo() {
            return new ProductUseCaseDto.GetProductsInfo(
                    memberId, brandId, SORTS.getOrDefault(sort, ProductUseCaseDto.ProductSort.LATEST), page, size);
        }
    }

    public record ProductSummaryResponse(
            Long productId,
            String name,
            String representativeImage,
            Long brandId,
            String brandName,
            Long price,
            int likeCount,
            boolean isSoldOut,
            boolean isLiked
    ) {
        public static ProductSummaryResponse from(ProductUseCaseDto.ProductSummaryResult result) {
            return new ProductSummaryResponse(
                    result.productId(),
                    result.name(),
                    result.representativeImage(),
                    result.brandId(),
                    result.brandName(),
                    result.price(),
                    result.likeCount(),
                    result.isSoldOut(),
                    result.isLiked()
            );
        }
    }

    public record GetProductResponse(
            Long productId,
            String name,
            String description,
            String representativeImage,
            Long brandId,
            String brandName,
            Long price,
            boolean isSoldOut,
            int likeCount,
            ProductUseCaseDto.ProductStatus status,
            ZonedDateTime openedAt
    ) {
        public static GetProductResponse from(ProductUseCaseDto.GetProductResult result) {
            return new GetProductResponse(
                    result.productId(),
                    result.name(),
                    result.description(),
                    result.representativeImage(),
                    result.brandId(),
                    result.brandName(),
                    result.price(),
                    result.isSoldOut(),
                    result.likeCount(),
                    result.status(),
                    result.openedAt()
            );
        }
    }
}
