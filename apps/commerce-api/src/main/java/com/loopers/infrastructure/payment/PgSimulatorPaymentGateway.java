package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgNotProcessedException;
import com.loopers.domain.payment.PgRejectedException;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.payment.PgTransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * 이 어댑터의 책임은 <b>PG 응답을 세 예외 타입 중 하나로 번역하는 것</b>이다.
 * 재시도할지 결제를 종결할지는 여기서 판단하지 않는다 (참고: Payment-010).
 */
@Slf4j
public class PgSimulatorPaymentGateway implements PaymentGateway {

    private static final String APPROVAL_PATH = "/api/v1/payments";
    private static final String HEADER_OF_USER_ID = "X-USER-ID";

    private final RestClient restClient;
    private final String callbackUrl;

    public PgSimulatorPaymentGateway(RestClient pgRestClient, String pgCallbackUrl) {
        this.restClient = pgRestClient;
        this.callbackUrl = pgCallbackUrl;
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
                        throw translate(res.getStatusCode(), command.orderNumber());
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
    private RuntimeException translate(HttpStatusCode status, String orderNumber) {
        log.warn("PG 승인 요청이 거절되었습니다. orderNumber={} status={}", orderNumber, status);

        if (status.value() == 429 || status.value() == 503) {
            return new PgNotProcessedException("PG가 요청을 받지 못했습니다. 잠시 후 다시 시도해주세요.");
        }

        return new PgRejectedException("PG 승인 요청이 거절되었습니다.");
    }

    /**
     * 스프링이 감싸는 예외 타입은 실패 단계마다 다르므로 <b>원인 사슬을 보고 판정한다.</b>
     * 연결이 안 맺혔으면 요청이 나가지 못한 것이고, 맺힌 뒤 응답이 없으면 처리 여부를 모른다.
     */
    private RuntimeException translate(RestClientException e, String orderNumber) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                log.warn("PG에 연결하지 못했습니다. orderNumber={}", orderNumber, e);
                return new PgNotProcessedException("PG에 연결하지 못했습니다.");
            }
            if (cause instanceof SocketTimeoutException timeout) {
                return translate(timeout, orderNumber, e);
            }
        }

        // 응답은 받았으나 해석하지 못한 경우. PG는 요청을 처리했으므로 거절과 같이 다룬다 (참고: Payment-009)
        log.error("PG 응답을 해석하지 못했습니다. orderNumber={}", orderNumber, e);
        return new PgRejectedException("PG 응답을 해석할 수 없습니다.");
    }

    /**
     * 연결 단계와 읽기 단계 타임아웃이 같은 타입이라 메시지로 가른다. JDK가 붙이는 문구는
     * 각각 "Connect timed out"과 "Read timed out"이다.
     */
    private RuntimeException translate(SocketTimeoutException timeout, String orderNumber, RestClientException e) {
        String message = timeout.getMessage() == null ? "" : timeout.getMessage().toLowerCase();

        if (message.contains("connect")) {
            log.warn("PG 연결이 시간 내에 맺어지지 않았습니다. orderNumber={}", orderNumber, e);
            return new PgNotProcessedException("PG에 연결하지 못했습니다.");
        }

        log.error("PG 응답을 받지 못해 처리 여부를 알 수 없습니다. orderNumber={}", orderNumber, e);
        return new PgResultUnknownException("PG 응답을 받지 못했습니다.", e);
    }

    private PaymentGatewayDto.Approval toApproval(PgApiResponse<PgApprovalResponse> response, String orderNumber) {
        if (response == null || response.data() == null || response.data().transactionKey() == null) {
            log.error("PG 응답을 해석할 수 없습니다. orderNumber={} response={}", orderNumber, response);
            throw new PgRejectedException("PG 응답을 해석할 수 없습니다.");
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
