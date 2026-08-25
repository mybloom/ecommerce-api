package com.loopers.interfaces.api.productlike;

import com.loopers.application.productlike.ProductLikeUseCase;
import com.loopers.application.productlike.ProductLikeUseCaseDto;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/like/products")
@RestController
public class ProductLikeV1Controller implements ProductLikeV1ApiSpec {

    private final ProductLikeUseCase productLikeUseCase;

    @PostMapping("/{productId}")
    @Override
    public ApiResponse<ProductLikeV1Dto.LikeResponse> like(
            @RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId,
            @PathVariable Long productId
    ) {
        ProductLikeUseCaseDto.LikeResult result = productLikeUseCase.like(
                new ProductLikeUseCaseDto.LikeInfo(memberId, productId));

        return ApiResponse.success(ProductLikeV1Dto.LikeResponse.from(result));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<ProductLikeV1Dto.UnlikeResponse> unlike(
            @RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId,
            @PathVariable Long productId
    ) {
        ProductLikeUseCaseDto.UnlikeResult result = productLikeUseCase.unlike(
                new ProductLikeUseCaseDto.UnlikeInfo(memberId, productId));

        return ApiResponse.success(ProductLikeV1Dto.UnlikeResponse.from(result));
    }
}
