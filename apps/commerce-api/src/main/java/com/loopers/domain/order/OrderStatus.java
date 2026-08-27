package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PENDING("Pending", "접수", "멱등키까지 확인해 접수됨"),
    AWAITING_PAYMENT("AwaitingPayment", "결제대기", "재고 확보 완료. 결제를 기다리는 상태"),
    ORDER_FAILED("OrderFailed", "주문실패", "접수 후 검증이나 재고 확보에 실패"),
    PAID("Paid", "결제완료", "결제가 완료된 상태"),
    PAYMENT_FAILED("PaymentFailed", "결제실패", "결제에 실패해 확보했던 재고가 복원된 상태");

    private final String code;
    private final String label;
    private final String description;

    /**
     * 이 상태에서 갈 수 있는 다음 상태들. 상수의 생성자 인자로는 담을 수 없어서
     * (아직 만들어지지 않은 상수를 참조하게 된다) static 블록에서 채운다.
     * 채우지 않은 상태는 빈 집합이라 어디로도 갈 수 없다.
     * AWAITING_PAYMENT에서 나가는 전이를 트리거하는 곳은 결제 흐름뿐이다 (참고: 07_payment.md Payment-004).
     */
    @Getter(AccessLevel.NONE)
    private Set<OrderStatus> allowedNextStatuses = Set.of();

    static {
        PENDING.allowedNextStatuses = Set.of(AWAITING_PAYMENT, ORDER_FAILED);
        AWAITING_PAYMENT.allowedNextStatuses = Set.of(PAID, PAYMENT_FAILED);
    }

    public boolean isPayable() {
        return this == AWAITING_PAYMENT;
    }

    public boolean isVisibleToUser() {
        return this != PENDING && this != ORDER_FAILED;
    }

    private boolean canTransitionTo(OrderStatus target) {
        return allowedNextStatuses.contains(target);
    }

    /**
     * 목표 상태로 전이할 수 없으면 예외를 던진다. 규칙과 그 집행이 함께 있어야 어긋나지 않는다.
     */
    public void requireTransitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new CoreException(ErrorType.CONFLICT,
                    "%s 상태에서 %s 상태로 전이할 수 없습니다.".formatted(label, target.label));
        }
    }
}
