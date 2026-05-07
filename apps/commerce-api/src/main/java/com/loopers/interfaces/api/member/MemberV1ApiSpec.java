package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member V1 API", description = "Member management operations")
public interface MemberV1ApiSpec {

    @Operation(summary = "회원가입")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 가입 성공")
    ApiResponse<MemberV1Dto.RegisterResponse> register(
            MemberV1Dto.RegisterRequest registerRequest
    );

    @Operation(
            summary = "내 정보 조회",
            description = "헤더 X-MEMBER-ID 로 식별된 사용자의 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    ApiResponse<MemberV1Dto.GetMemberProfileResponse> getMemberProfile(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId
    );
}
