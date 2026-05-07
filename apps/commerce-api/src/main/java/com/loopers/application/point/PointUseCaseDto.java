package com.loopers.application.point;

import com.loopers.domain.point.PointServiceDto;

public class PointUseCaseDto {

    public record ChargeInfo(
        Long memberId,
        Long amount
    ) {
        public PointServiceDto.ChargeCommand toCommand() {
            return new PointServiceDto.ChargeCommand(memberId, amount);
        }
    }

    public record ChargeResult(
        Long memberId,
        Long amount,
        Long balance
    ) {
        public static ChargeResult from(PointServiceDto.ChargeQuery query) {
            return new PointUseCaseDto.ChargeResult(
                    query.memberId(), query.amount(), query.balance()
            );
        }
    }

    public record RetrieveResult(
        Long memberId,
        Long balance
    ) {
        public static RetrieveResult from(PointServiceDto.RetrieveQuery query) {
            return new PointUseCaseDto.RetrieveResult(query.memberId(), query.balance());
        }
    }

    public record RetrieveInfo(
            Long memberId
    ) {
        public PointServiceDto.RetrieveCommand toCommand() {
            return new PointServiceDto.RetrieveCommand(memberId);
        }
    }
}
