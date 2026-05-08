package com.loopers.interfaces.api.point;

import com.loopers.application.point.PointUseCase;
import com.loopers.application.point.PointUseCaseDto;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/v1/points")
@RestController
public class PointV1Controller implements PointV1ApiSpec {

    private final PointUseCase pointUseCase;

    @PostMapping("/charge")
    @Override
    public ApiResponse<PointV1Dto.ChargeResponse> charge(
            @RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId,
            @Valid @RequestBody PointV1Dto.ChargeRequest request
    ) {
        PointUseCaseDto.ChargeResult result = pointUseCase.charge(request.toInfo(memberId));

        return ApiResponse.success(PointV1Dto.ChargeResponse.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<PointV1Dto.RetrieveResponse> retrieve(
            @RequestHeader(name = "X-MEMBER-ID", required = true) Long memberId
    ) {
        PointUseCaseDto.RetrieveResult result = pointUseCase.retrieve(new PointUseCaseDto.RetrieveInfo(memberId));

        return ApiResponse.success(PointV1Dto.RetrieveResponse.from(result));
    }
}
