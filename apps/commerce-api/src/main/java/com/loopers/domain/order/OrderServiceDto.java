package com.loopers.domain.order;

import java.util.List;

public class OrderServiceDto {

    public record FindByIdempotencyKeyCommand(IdempotencyKey idempotencyKey) {
    }

    public record DraftCommand(Long memberId, IdempotencyKey idempotencyKey) {
    }

    public record ConfirmCommand(Long orderId, List<OrderLine> lines) {
    }

    public record MarkOrderFailedCommand(Long orderId) {
    }
}
