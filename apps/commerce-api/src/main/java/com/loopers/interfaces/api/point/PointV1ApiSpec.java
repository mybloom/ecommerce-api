package com.loopers.interfaces.api.point;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Point V1 API", description = "Point management operations")
public interface PointV1ApiSpec {

    @Operation(summary = "포인트 충전", description = "헤더 X-MEMBER-ID 로 식별된 사용자의 포인트를 충전합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "포인트 충전 성공")
    ApiResponse<PointV1Dto.ChargeResponse> charge(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId,
            PointV1Dto.ChargeRequest chargeRequest
    );

    @Operation(summary = "포인트 조회", description = "헤더 X-MEMBER-ID 로 식별된 사용자의 포인트 잔액을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "포인트 조회 성공")
    ApiResponse<PointV1Dto.RetrieveResponse> retrieve(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId
    );
}
