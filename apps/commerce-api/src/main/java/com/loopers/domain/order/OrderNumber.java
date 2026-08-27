package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.*;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    public static OrderNumber of(String value) {
        if (value == null || !ORDER_NUMBER_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문번호 형식이 바르지 않습니다: " + value);
        }

        return new OrderNumber(value);
    }

    /**
     * {@code yyyyMMdd-8자리 랜덤 영숫자} 형식으로 채번한다 (참고: Order-011).
     * <p>
     * 키 공간이 하루 약 2.8조(36^8)라 <b>충돌 확률이 0은 아니다.</b> 하루 10만 건이면 약 0.18%다.
     * 충돌하면 UNIQUE 제약에 걸려 그 요청 하나가 CONFLICT를 받고 끝나며, 데이터가 깨지지는 않는다.
     * 자릿수 확대나 ULID 전환은 충돌이 모니터링에 잡힐 때 검토한다 (참고: Order-011 재검토 시점).
     */
    public static OrderNumber generate() {
        StringBuilder randomPart = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            randomPart.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }

        return of(ZonedDateTime.now().format(DATE_FORMAT) + "-" + randomPart);
    }
}
