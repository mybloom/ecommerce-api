package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductServiceDto;
import com.loopers.domain.product.ProductSummary;
import com.loopers.domain.shared.PageQuery;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

public class ProductUseCaseDto {

    /**
     * API 계약이 도메인 enum 에 묶이지 않도록 레이어마다 따로 둔다.
     * 도메인에서 상수를 바꿔도 여기서 변환이 깨지며 드러난다.
     */
    public enum ProductStatus {
        ON_SALE, OFF_SALE, HIDDEN
    }

    public enum ProductSort {
        LATEST, PRICE_DESC, LIKE_DESC
    }

    public record GetProductsInfo(@Nullable Long memberId, @Nullable Long brandId, ProductSort sort, int page, int size) {
        public ProductServiceDto.RetrieveSummariesCommand toCommand() {
            return new ProductServiceDto.RetrieveSummariesCommand(
                    brandId,
                    com.loopers.domain.product.ProductSort.valueOf(sort.name()),
                    new PageQuery(page, size)
            );
        }
    }

    public record ProductSummaryResult(
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
        /**
         * @param isLiked 비로그인이면 false (참고: Product-007)
         */
        public static ProductSummaryResult from(ProductSummary summary, boolean isLiked) {
            return new ProductSummaryResult(
                    summary.productId(),
                    summary.name(),
                    summary.representativeImage(),
                    summary.brandId(),
                    summary.brandName(),
                    summary.price().getAmount(),
                    summary.likeCount(),
                    summary.isSoldOut(),
                    isLiked
            );
        }
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
