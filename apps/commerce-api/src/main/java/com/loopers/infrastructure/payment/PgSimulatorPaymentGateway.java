package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgConnectionFailedException;
import com.loopers.domain.payment.PgHostUnresolvedException;
import com.loopers.domain.payment.PgNotProcessedException;
import com.loopers.domain.payment.PgRateLimitedException;
import com.loopers.domain.payment.PgRejectedException;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.payment.PgUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 이 어댑터의 책임은 <b>PG 응답을 세 예외 타입 중 하나로 번역하는 것</b>이다.
 * 재시도할지 결제를 종결할지는 여기서 판단하지 않는다 (참고: Payment-010).
 * <p>
 * <b>거래가 안 생겼음이 확실하지 않으면 {@link PgResultUnknownException}이다.</b> 거절은 PG가 4xx로
 * 직접 말해 준 경우뿐이다 (참고: 07_payment.md D절).
 */
@Slf4j
public class PgSimulatorPaymentGateway implements PaymentGateway {

    private static final String APPROVAL_PATH = "/api/v1/payments";
    private static final String HEADER_OF_USER_ID = "X-USER-ID";

    private final RestClient restClient;
    private final String callbackUrl;
    private final Clock clock;

    public PgSimulatorPaymentGateway(RestClient pgRestClient, String pgCallbackUrl) {
        this(pgRestClient, pgCallbackUrl, Clock.systemUTC());
    }

    /**
     * @param clock {@code Retry-After}가 날짜로 오면 지금과의 차이를 구한다. 테스트가 시각을 고정하려고 바꾼다
     */
    PgSimulatorPaymentGateway(RestClient pgRestClient, String pgCallbackUrl, Clock clock) {
        this.restClient = pgRestClient;
        this.callbackUrl = pgCallbackUrl;
        this.clock = clock;
    }

    @Override
    public PaymentGatewayDto.Approval requestApproval(PaymentGatewayDto.ApprovalCommand command) {
        PgApprovalRequest request = PgApprovalRequest.from(command, callbackUrl);

        try {
            PgApiResponse<PgApprovalResponse> response = restClient.post()
                    .uri(APPROVAL_PATH)
                    .header(HEADER_OF_USER_ID, String.valueOf(command.memberId()))
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw translate(res.getStatusCode(), res.getHeaders(), command.orderNumber());
                    })
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});

            return toApproval(response, command.orderNumber());
        } catch (PgNotProcessedException | PgRejectedException | PgResultUnknownException e) {
            throw e;
        } catch (RestClientException e) {
            throw translate(e, command.orderNumber());
        }
    }

    /**
     * PG가 준 메시지는 여기서 로그로만 흘린다. 사용자 응답과 failureReason에는 우리 문구를 쓴다
     * (참고: Payment-009).
     */
    private RuntimeException translate(HttpStatusCode status, HttpHeaders headers, String orderNumber) {
        if (status.value() == 429) {
            Duration retryAfter = retryAfter(headers);
            log.warn("PG가 요청량 초과로 요청을 받지 않았습니다. orderNumber={} retryAfter={}", orderNumber, retryAfter);
            return new PgRateLimitedException("PG가 요청을 받지 못했습니다. 잠시 후 다시 시도해주세요.", retryAfter);
        }
        if (status.value() == 503) {
            Duration retryAfter = retryAfter(headers);
            log.warn("PG가 일시적으로 요청을 받지 않았습니다. orderNumber={} retryAfter={}", orderNumber, retryAfter);
            return new PgUnavailableException("PG가 요청을 받지 못했습니다. 잠시 후 다시 시도해주세요.", retryAfter);
        }

        // 5xx는 PG가 거래를 기록하기 전에 실패했는지 후에 실패했는지 알 수 없다. 502·504는 앞단 프록시가 냈을 수도 있다
        if (status.is5xxServerError()) {
            log.error("PG가 처리 여부를 알 수 없는 응답을 주었습니다. orderNumber={} status={}", orderNumber, status);
            return new PgResultUnknownException("PG가 처리 여부를 알 수 없는 응답을 주었습니다.");
        }

        log.warn("PG 승인 요청이 거절되었습니다. orderNumber={} status={}", orderNumber, status);
        return new PgRejectedException("PG 승인 요청이 거절되었습니다.");
    }

    /**
     * 스프링이 감싸는 예외 타입은 실패 단계마다 다르므로 <b>원인 사슬을 보고 판정한다.</b>
     * 연결이 안 맺혔으면 요청이 나가지 못한 것이고, 맺힌 뒤 응답이 없으면 처리 여부를 모른다.
     */
    private RuntimeException translate(RestClientException e, String orderNumber) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException) {
                log.error("PG 주소를 찾지 못했습니다. 설정을 확인하세요. orderNumber={}", orderNumber, e);
                return new PgHostUnresolvedException("PG에 연결하지 못했습니다.");
            }
            if (cause instanceof ConnectException) {
                log.warn("PG에 연결하지 못했습니다. orderNumber={}", orderNumber, e);
                return new PgConnectionFailedException("PG에 연결하지 못했습니다.");
            }
            if (cause instanceof SocketTimeoutException timeout) {
                return translate(timeout, orderNumber, e);
            }
        }

        // 응답을 받았으나 해석하지 못했거나 본문을 읽다 끊긴 경우. PG가 거래를 만들었을 수 있다 (참고: 07_payment.md D절)
        log.error("PG 응답을 해석하지 못했습니다. orderNumber={}", orderNumber, e);
        return new PgResultUnknownException("PG 응답을 해석할 수 없습니다.", e);
    }

    /**
     * 연결 단계와 읽기 단계 타임아웃이 같은 타입이라 메시지로 가른다. JDK가 붙이는 문구는
     * 각각 "Connect timed out"과 "Read timed out"이다.
     */
    private RuntimeException translate(SocketTimeoutException timeout, String orderNumber, RestClientException e) {
        String message = timeout.getMessage() == null ? "" : timeout.getMessage().toLowerCase();

        if (message.contains("connect")) {
            log.warn("PG 연결이 시간 내에 맺어지지 않았습니다. orderNumber={}", orderNumber, e);
            return new PgConnectionFailedException("PG에 연결하지 못했습니다.");
        }

        log.error("PG 응답을 받지 못해 처리 여부를 알 수 없습니다. orderNumber={}", orderNumber, e);
        return new PgResultUnknownException("PG 응답을 받지 못했습니다.", e);
    }

    /**
     * {@code Retry-After}는 초(예: {@code 2})나 날짜(예: {@code Sun, 04 Oct 2026 05:00:03 GMT})로 온다.
     * 지난 날짜는 0이다. 해석하지 못하면 값이 없는 것과 같이 다룬다 — 이 값 때문에 재시도할 수 있는 실패를
     * 다른 실패로 바꾸지 않는다.
     */
    private @Nullable Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                return Duration.ofSeconds(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        try {
            ZonedDateTime at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration untilThen = Duration.between(clock.instant(), at.toInstant());
            return untilThen.isNegative() ? Duration.ZERO : untilThen;
        } catch (DateTimeException e) {
            return null;
        }
    }

    private PaymentGatewayDto.Approval toApproval(PgApiResponse<PgApprovalResponse> response, String orderNumber) {
        if (response == null || response.data() == null || response.data().transactionKey() == null) {
            // 2xx는 PG가 거래를 만들었다는 뜻이다. 본문을 못 읽었다고 거절로 볼 수 없다
            log.error("PG 응답을 해석할 수 없습니다. orderNumber={} response={}", orderNumber, response);
            throw new PgResultUnknownException("PG 응답을 해석할 수 없습니다.");
        }

        PgApprovalResponse data = response.data();
        log.info("PG 승인 요청이 접수되었습니다. orderNumber={} transactionKey={}", orderNumber, data.transactionKey());

        return new PaymentGatewayDto.Approval(data.transactionKey(), data.status());
    }

    record PgApiResponse<T>(T data) {
    }

    record PgApprovalResponse(String transactionKey, PgTransactionStatus status, String reason) {
    }

    record PgApprovalRequest(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {
        static PgApprovalRequest from(PaymentGatewayDto.ApprovalCommand command, String callbackUrl) {
            return new PgApprovalRequest(
                    command.orderNumber(),
                    command.cardType().name(),
                    command.cardNo(),
                    command.amount().getAmount(),
                    callbackUrl);
        }
    }
}
