package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    PENDING("Pending", "승인대기", "결제가 접수되어 결과를 기다리는 상태"),
    APPROVED("Approved", "승인완료", "결제가 승인된 상태"),
    FAILED("Failed", "결제실패", "승인에 실패해 주문의 재고가 복원된 상태");

    private final String code;
    private final String label;
    private final String description;

    /**
     * 이 상태에서 갈 수 있는 다음 상태들. 상수의 생성자 인자로는 담을 수 없어서
     * (아직 만들어지지 않은 상수를 참조하게 된다) static 블록에서 채운다.
     * 채우지 않은 상태는 빈 집합이라 어디로도 갈 수 없다 — 종결된 결제가 그렇다.
     */
    @Getter(AccessLevel.NONE)
    private Set<PaymentStatus> allowedNextStatuses = Set.of();

    static {
        PENDING.allowedNextStatuses = Set.of(APPROVED, FAILED);
    }

    public boolean isFinalized() {
        return this == APPROVED || this == FAILED;
    }

    private boolean canTransitionTo(PaymentStatus target) {
        return allowedNextStatuses.contains(target);
    }

    /**
     * 목표 상태로 전이할 수 없으면 예외를 던진다. 규칙과 그 집행이 함께 있어야 어긋나지 않는다.
     * OrderStatus와 같은 방식이다 — 두 상태 기계가 서로 다르게 검사하면 어느 쪽이 규칙인지 알 수 없게 된다.
     */
    public void requireTransitionTo(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new CoreException(ErrorType.CONFLICT,
                    "%s 상태에서 %s 상태로 전이할 수 없습니다.".formatted(label, target.label));
        }
    }
}
