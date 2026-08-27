package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.*;

/**
 * 중복 주문 요청을 식별하는 키 (참고: Order-004).
 * 클라이언트가 매 주문 시도마다 새로 만들어 Idempotency-Key 헤더로 보낸다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@EqualsAndHashCode
public class IdempotencyKey {

    public static final int MAX_LENGTH = 100;

    private final String value;

    public static IdempotencyKey of(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "중복 요청 식별자는 빈 값이 될 수 없습니다.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "중복 요청 식별자는 " + MAX_LENGTH + "자를 넘을 수 없습니다. length=" + value.length());
        }

        return new IdempotencyKey(value);
    }
}
