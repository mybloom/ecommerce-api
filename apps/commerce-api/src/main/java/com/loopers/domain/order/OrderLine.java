package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Product;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.util.Objects.requireNonNull;

/**
 * 주문에 담긴 상품 한 종류. Order 애그리거트 내부이며 Order를 통해서만 접근한다 (참고: Order-008).
 */
@Getter
@Entity
@Table(name = "order_line")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    @Column(nullable = false)
    private int quantity;

    /**
     * 상품명과 단가를 이 시점에 복사해 보관한다 (참고: Order-001).
     * 이후 상품이 바뀌거나 삭제돼도 주문 내용은 그대로 남는다.
     */
    public static OrderLine of(Product product, int quantity) {
        requireNonNull(product, "주문할 상품은 필수입니다.");
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다. quantity=" + quantity);
        }

        OrderLine line = new OrderLine();

        line.productId = requireNonNull(product.getId(), "상품 식별자는 필수입니다.");
        line.productName = product.getName();
        line.unitPrice = product.getPrice();
        line.quantity = quantity;

        return line;
    }

    public Money lineAmount() {
        return unitPrice.multiply(quantity);
    }
}
