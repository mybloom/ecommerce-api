package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductUseCase;
import com.loopers.application.product.ProductUseCaseDto;
import com.loopers.application.shared.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@RestController
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductUseCase productUseCase;

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.GetProductResponse> getProduct(@PathVariable Long productId) {
        ProductUseCaseDto.GetProductResult result = productUseCase.getProduct(
                new ProductUseCaseDto.GetProductInfo(productId));

        return ApiResponse.success(ProductV1Dto.GetProductResponse.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>> getProducts(
            @RequestHeader(name = "X-MEMBER-ID", required = false) @Nullable Long memberId,
            @RequestParam(required = false) @Nullable Long brandId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ProductV1Dto.GetProductsRequest request = new ProductV1Dto.GetProductsRequest(memberId, brandId, sort, page, size);
        PageResult<ProductUseCaseDto.ProductSummaryResult> result = productUseCase.getProducts(request.toInfo());

        return ApiResponse.success(PageResponse.from(result, ProductV1Dto.ProductSummaryResponse::from));
    }
}
