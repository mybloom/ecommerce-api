package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductServiceDto;
import com.loopers.domain.product.ProductStatus;

import java.time.ZonedDateTime;

public class ProductUseCaseDto {

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
                    product.getStatus(),
                    product.getOpenedAt()
            );
        }
    }
}
