package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "order_number", nullable = false, unique = true))
    private OrderNumber orderNumber;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "idempotency_key", nullable = false, unique = true))
    private IdempotencyKey idempotencyKey;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Column(nullable = false)
    private ZonedDateTime orderedAt;

    @Nullable
    private ZonedDateTime paidAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderLine> lines = new ArrayList<>();

    /**
     * 주문을 접수해 초안 상태로 만든다. 이 시점의 주문은 "누가 주문을 시도했다"는 사실만 담는다 (참고: Order-004).
     */
    public static Order draft(Long memberId, OrderNumber orderNumber, IdempotencyKey idempotencyKey) {
        Order order = new Order();

        order.memberId = requireNonNull(memberId, "주문 회원은 필수입니다.");
        order.orderNumber = requireNonNull(orderNumber, "주문번호는 필수입니다.");
        order.idempotencyKey = requireNonNull(idempotencyKey, "중복 요청 식별자는 필수입니다.");
        order.status = OrderStatus.PENDING;
        order.totalAmount = Money.ZERO;
        order.orderedAt = ZonedDateTime.now();

        return order;
    }

    /**
     * 라인을 붙이고 총액을 확정한 뒤 결제 대기로 전이한다.
     * 라인이 붙는 시점은 여기 한 곳뿐이다 — PENDING 주문에 라인을 끼워 넣을 경로가 없다 (참고: Order-008).
     */
    public void confirm(List<OrderLine> lines) {
        status.requireTransitionTo(OrderStatus.AWAITING_PAYMENT);

        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 라인은 1개 이상이어야 합니다.");
        }
        if (hasDuplicatedProduct(lines)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 라인에 같은 상품이 중복될 수 없습니다.");
        }

        this.lines.addAll(lines);
        this.totalAmount = lines.stream()
                .map(OrderLine::lineAmount)
                .reduce(Money.ZERO, Money::add);
        this.status = OrderStatus.AWAITING_PAYMENT;
    }

    public void markOrderFailed() {
        status.requireTransitionTo(OrderStatus.ORDER_FAILED);

        this.status = OrderStatus.ORDER_FAILED;
    }

    /**
     * 결제 완료 처리. 이 전이를 트리거하는 곳은 결제 흐름뿐이다 (참고: 07_payment.md Payment-004).
     */
    public void pay() {
        status.requireTransitionTo(OrderStatus.PAID);

        this.status = OrderStatus.PAID;
        this.paidAt = ZonedDateTime.now();
    }

    /**
     * 결제 실패 처리. <b>확보했던 재고를 되돌리는 것은 호출자의 몫이다</b> — 차감이 다른 트랜잭션에서
     * 일어났으므로 롤백으로 되돌아오지 않는다 (참고: Order-007).
     */
    public void markPaymentFailed() {
        status.requireTransitionTo(OrderStatus.PAYMENT_FAILED);

        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isPayable() {
        return status.isPayable();
    }

    /**
     * 총액 = 라인 합계라는 불변식을 밖에서 깨지 못하도록 수정 불가 목록을 돌려준다 (참고: Order-008).
     */
    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    private static boolean hasDuplicatedProduct(List<OrderLine> lines) {
        return lines.stream().map(OrderLine::getProductId).distinct().count() != lines.size();
    }
}
