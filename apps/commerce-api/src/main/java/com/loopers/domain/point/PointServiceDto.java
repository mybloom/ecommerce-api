package com.loopers.domain.point;

public class PointServiceDto {
    public record CreateInitialCommand(Long memberId) {
    }

    public record ChargeCommand(
            Long memberId,
            Money amount
    ) {
        public ChargeCommand(Long memberId, Long amount) {
            this(memberId, Money.of(amount));
        }
    }

    public record ChargeQuery(
            Long memberId,
            Long amount,
            Long balance
    ) {
    }

    public record RetrieveQuery(
            Long memberId,
            Long balance
    ) {
    }

    public record RetrieveCommand(
            Long memberId
    ) {
    }
}
