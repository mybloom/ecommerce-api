package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberUseCase;
import com.loopers.application.member.MemberUseCaseDto;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
@RestController
public class MemberV1Controller implements MemberV1ApiSpec {
    private final MemberUseCase memberUseCase;

    @PostMapping
    @Override
    public ApiResponse<MemberV1Dto.RegisterResponse> register(@Valid @RequestBody MemberV1Dto.RegisterRequest request) {
        MemberUseCaseDto.RegisterResult result = memberUseCase.register(request.toInfo());

        return ApiResponse.success(MemberV1Dto.RegisterResponse.from(result));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<MemberV1Dto.GetMemberProfileResponse> getMemberProfile(@RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId) {
        MemberUseCaseDto.GetMemberProfileResult result = memberUseCase.getMemberProfile(new MemberUseCaseDto.GetMemberProfileInfo(memberId));

        return ApiResponse.success(MemberV1Dto.GetMemberProfileResponse.from(result));
    }
}
