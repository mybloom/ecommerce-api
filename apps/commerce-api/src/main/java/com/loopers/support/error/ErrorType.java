package com.loopers.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),

    /** 상류 시스템이 응답을 제대로 주지 못함. 우리 서버는 정상이므로 INTERNAL_ERROR와 구분한다 (참고: Payment-008) */
    BAD_GATEWAY(HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY.getReasonPhrase(), "외부 시스템 연동에 실패했습니다."),

    /**
     * PG가 요청을 처리하지 않은 실패를 원인별로 나눈다. HTTP 상태는 모두 502라 reason phrase로는 구분되지 않으므로
     * code에 고유 문자열을 쓴다. 클라이언트는 이 code로 안내를 고른다 (참고: 07_payment.md E.4)
     */
    PG_CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "PG_CONNECTION_FAILED", "결제 서버에 연결하지 못했습니다. 잠시 후 다시 주문해 주세요."),
    PG_RATE_LIMITED(HttpStatus.BAD_GATEWAY, "PG_RATE_LIMITED", "결제 요청이 몰리고 있습니다. 잠시 후 다시 주문해 주세요."),
    PG_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "PG_UNAVAILABLE", "결제 서버가 일시적으로 응답하지 않습니다. 잠시 후 다시 주문해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
