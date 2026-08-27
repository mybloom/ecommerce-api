package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@EqualsAndHashCode
@Embeddable
public class StockQuantity {

    private final int value;

    public static StockQuantity of(int value) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다. value=" + value);
        }
        return new StockQuantity(value);
    }

    public boolean isSoldOut() {
        return value == 0;
    }

    public boolean isEnough(int quantity) {
        return this.value >= quantity;
    }

    public StockQuantity subtract(int quantity) {
        requirePositive(quantity, "차감");

        return StockQuantity.of(Math.subtractExact(this.value, quantity));
    }

    public StockQuantity add(int quantity) {
        requirePositive(quantity, "증가");

        return StockQuantity.of(Math.addExact(this.value, quantity));
    }

    private static void requirePositive(int quantity, String action) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    action + " 수량은 1 이상이어야 합니다. quantity=" + quantity);
        }
    }
}
