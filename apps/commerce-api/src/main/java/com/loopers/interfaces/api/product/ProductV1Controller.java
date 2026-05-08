package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductUseCase;
import com.loopers.application.product.ProductUseCaseDto;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
