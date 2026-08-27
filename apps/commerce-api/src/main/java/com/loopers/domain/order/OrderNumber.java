package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.*;

import java.util.regex.Pattern;

/**
 * 사용자에게 노출하는 주문번호 (참고: Order-011).
 * API가 주고받는 주문 식별자는 전부 이 값이며, 내부 식별자 id는 어디로도 나가지 않는다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@EqualsAndHashCode
public class OrderNumber {

    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("^\\d{8}-[0-9A-Z]{8}$");

    private final String value;

    public static OrderNumber of(String value) {
        if (value == null || !ORDER_NUMBER_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문번호 형식이 바르지 않습니다: " + value);
        }

        return new OrderNumber(value);
    }
}
