package com.loopers.application.product;

import com.loopers.application.shared.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.BrandServiceDto;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSummary;
import com.loopers.domain.productlike.ProductLikeService;
import com.loopers.domain.productlike.ProductLikeServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class ProductUseCase {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductLikeService productLikeService;

    public ProductUseCaseDto.GetProductResult getProduct(ProductUseCaseDto.GetProductInfo info) {
        Product product = productService.retrieve(info.toCommand());

        Brand brand = brandService.retrieve(new BrandServiceDto.RetrieveCommand(product.getBrandId()));

        return ProductUseCaseDto.GetProductResult.from(product, brand);
    }

    /**
     * 로그인했으면 조회한 페이지의 상품 id 로 좋아요 여부를 한 번에 조회해 붙인다 (참고: Product-006).
     * 비로그인이면 조회하지 않고 모두 false 다 (참고: Product-007).
     */
    @Transactional(readOnly = true)
    public PageResult<ProductUseCaseDto.ProductSummaryResult> getProducts(ProductUseCaseDto.GetProductsInfo info) {
        com.loopers.domain.shared.PageResult<ProductSummary> summaries = productService.retrieveSummaries(info.toCommand());

        Long memberId = info.memberId();
        if (memberId == null) {
            return PageResult.from(summaries, summary -> ProductUseCaseDto.ProductSummaryResult.from(summary, false));
        }

        List<Long> productIds = summaries.content().stream().map(ProductSummary::productId).toList();
        Set<Long> likedProductIds = productLikeService.retrieveLikedProductIds(
                new ProductLikeServiceDto.RetrieveLikedProductIdsCommand(memberId, productIds));

        return PageResult.from(summaries, summary ->
                ProductUseCaseDto.ProductSummaryResult.from(summary, likedProductIds.contains(summary.productId())));
    }
}
