package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductServiceDto;

import java.time.ZonedDateTime;

public class ProductUseCaseDto {

    /**
     * API 계약이 도메인 enum 에 묶이지 않도록 레이어마다 따로 둔다.
     * 도메인에서 상수를 바꿔도 여기서 변환이 깨지며 드러난다.
     */
    public enum ProductStatus {
        ON_SALE, OFF_SALE, HIDDEN
    }

    public record GetProductInfo(Long productId) {
        public ProductServiceDto.RetrieveCommand toCommand() {
            return new ProductServiceDto.RetrieveCommand(productId);
        }
    }

    public record GetProductResult(
            Long productId,
            String name,
            String description,
            String representativeImage,
            Long brandId,
            String brandName,
            Long price,
            boolean isSoldOut,
            int likeCount,
            ProductStatus status,
            ZonedDateTime openedAt
    ) {
        public static GetProductResult from(Product product, Brand brand) {
            return new GetProductResult(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getRepresentativeImage(),
                    brand.getId(),
                    brand.getName(),
                    product.getPrice().getAmount(),
                    product.isSoldOut(),
                    product.getLikeCount(),
                    ProductStatus.valueOf(product.getStatus().name()),
                    product.getOpenedAt()
            );
        }
    }
}
