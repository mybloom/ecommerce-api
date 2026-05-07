package com.loopers.interfaces.api.point;

import com.loopers.application.point.PointUseCaseDto;
import jakarta.validation.constraints.Min;

public class PointV1Dto {
    public record ChargeRequest(@Min(1) Long amount) {
        public PointUseCaseDto.ChargeInfo toInfo(Long memberId) {
            return new PointUseCaseDto.ChargeInfo(memberId, amount);
        }
    }

    public record ChargeResponse(
            Long memberId,
            Long balance
    ) {
        public static ChargeResponse from(PointUseCaseDto.ChargeResult result) {
            return new ChargeResponse(result.memberId(), result.balance());
        }
    }

    public record RetrieveResponse(
            Long memberId,
            Long balance
    ) {
        public static RetrieveResponse from(PointUseCaseDto.RetrieveResult result) {
            return new RetrieveResponse(result.memberId(), result.balance());
        }
    }
}
