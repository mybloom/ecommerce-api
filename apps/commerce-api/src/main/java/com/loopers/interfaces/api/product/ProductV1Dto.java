package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductUseCaseDto;

import java.time.ZonedDateTime;

public class ProductV1Dto {

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
