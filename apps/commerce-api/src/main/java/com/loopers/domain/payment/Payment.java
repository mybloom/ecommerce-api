package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 한 주문에 결제는 하나다. UNIQUE를 둔 것은 동시 결제 요청을 DB가 막게 하기 위해서다 (참고: Payment-001).
     */
    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Nullable
    private String transactionKey;

    @Nullable
    private ZonedDateTime approvedAt;

    @Nullable
    private String failureReason;

    /**
     * 결제를 접수해 승인대기 상태로 만든다. 금액은 주문 총액을 복사한 값이다 (참고: Payment-002).
     */
    public static Payment request(Long orderId, Long memberId, PaymentMethod method, Money amount) {
        Payment payment = new Payment();

        payment.orderId = requireNonNull(orderId, "결제 대상 주문은 필수입니다.");
        payment.memberId = requireNonNull(memberId, "결제하는 회원은 필수입니다.");
        payment.method = requireNonNull(method, "결제 수단은 필수입니다.");
        payment.amount = requireNonNull(amount, "결제 금액은 필수입니다.");
        payment.status = PaymentStatus.PENDING;

        if (!amount.isPositive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 1 이상이어야 합니다. amount=" + amount);
        }

        return payment;
    }

    /**
     * 승인 확정. <b>PG 거래 식별자의 유무가 곧 결제 수단의 불변식이다</b> — CARD는 있어야 하고
     * POINT는 없어야 한다 (참고: B.1 불변식표).
     */
    public void approve(@Nullable String transactionKey) {
        status.requireTransitionTo(PaymentStatus.APPROVED);
        requireTransactionKeyMatchesMethod(transactionKey);

        this.status = PaymentStatus.APPROVED;
        this.transactionKey = transactionKey;
        this.approvedAt = ZonedDateTime.now();
    }

    private void requireTransactionKeyMatchesMethod(@Nullable String transactionKey) {
        boolean present = transactionKey != null && !transactionKey.isBlank();

        if (method == PaymentMethod.CARD && !present) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 결제 승인에는 PG 거래 식별자가 필요합니다.");
        }
        if (method != PaymentMethod.CARD && present) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "%s 결제에는 PG 거래 식별자가 없어야 합니다.".formatted(method.getLabel()));
        }
    }

    /**
     * 실패 확정. <b>확보했던 재고를 되돌리는 것은 호출자의 몫이다</b> (참고: Order-007, Payment-006).
     */
    public void fail(@Nullable String reason) {
        status.requireTransitionTo(PaymentStatus.FAILED);

        if (reason == null || reason.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패 사유는 필수입니다.");
        }

        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * 종결된 결제에 두 번째 결과를 적용하지 않기 위한 판정 (참고: Payment-005).
     */
    public boolean isFinalized() {
        return status.isFinalized();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
