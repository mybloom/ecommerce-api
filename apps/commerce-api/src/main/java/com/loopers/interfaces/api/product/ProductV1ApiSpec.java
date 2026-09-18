package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;

@Tag(name = "Product V1 API", description = "Product information operations")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 조회", description = "productId로 ON_SALE 상태인 상품 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    ApiResponse<ProductV1Dto.GetProductResponse> getProduct(Long productId);

    @Operation(summary = "상품 목록 조회", description = "ON_SALE 상품 중 ACTIVE 브랜드의 상품을 정렬·페이징해 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공. 결과가 없으면 빈 목록"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "brandId·page·size 가 숫자가 아님")
    })
    ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>> getProducts(
            @Parameter(name = "X-MEMBER-ID", in = ParameterIn.HEADER,
                    description = "로그인한 회원. 없으면 모든 상품의 isLiked 가 false") @Nullable Long memberId,
            @Parameter(description = "브랜드 필터. 없으면 전체") @Nullable Long brandId,
            @Parameter(description = "latest(기본) | priceDesc | likeDesc. 그 외 값은 latest") String sort,
            @Parameter(description = "0부터 시작. 음수는 0으로 보정") int page,
            @Parameter(description = "기본 20. 1~100 범위로 보정") int size
    );
}
