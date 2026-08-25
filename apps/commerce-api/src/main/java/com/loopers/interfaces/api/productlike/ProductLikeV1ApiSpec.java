package com.loopers.interfaces.api.productlike;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "ProductLike V1 API", description = "Product like operations")
public interface ProductLikeV1ApiSpec {

    @Operation(
            summary = "상품 좋아요 등록",
            description = "헤더 X-MEMBER-ID 로 식별된 사용자가 ON_SALE 상태인 상품에 좋아요를 등록합니다. "
                    + "이미 좋아요한 상태여도 성공으로 응답하며, 이 경우 좋아요 수는 변하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    ApiResponse<ProductLikeV1Dto.LikeResponse> like(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId,
            @Parameter(
                    name = "productId",
                    required = true,
                    in = ParameterIn.PATH,
                    description = "좋아요 대상 상품 식별자"
            ) Long productId
    );

    @Operation(
            summary = "상품 좋아요 취소",
            description = "헤더 X-MEMBER-ID 로 식별된 사용자가 이전에 누른 좋아요를 취소합니다. "
                    + "판매 상태나 브랜드 상태와 무관하게 취소할 수 있으며, "
                    + "좋아요하지 않은 상태여도 성공으로 응답하고 이 경우 좋아요 수는 변하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 취소 성공")
    })
    ApiResponse<ProductLikeV1Dto.UnlikeResponse> unlike(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId,
            @Parameter(
                    name = "productId",
                    required = true,
                    in = ParameterIn.PATH,
                    description = "좋아요 취소 대상 상품 식별자"
            ) Long productId
    );
}
