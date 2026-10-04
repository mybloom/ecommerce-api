package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgConnectionFailedException;
import com.loopers.domain.payment.PgHostUnresolvedException;
import com.loopers.domain.payment.PgRateLimitedException;
import com.loopers.domain.payment.PgRejectedException;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.payment.PgUnavailableException;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 이 어댑터의 책임은 <b>PG 응답을 세 예외 타입 중 하나로 번역하는 것</b>이다 (참고: Payment-010).
 * 번역이 틀리면 재시도해도 되는 것을 포기하거나, 확정하면 안 되는 것을 확정한다.
 * <b>거래가 안 생겼음이 확실하지 않으면 PgResultUnknownException이다</b> (참고: 07_payment.md D절).
 */
class PgSimulatorPaymentGatewayTest {

    private static final String BASE_URL = "http://pg.test";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/pg/callback";
    private static final Long MEMBER_ID = 135135L;
    private static final String ORDER_NUMBER = "20260828-A3F9K2QP";
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final Long AMOUNT = 5_000L;

    private static PaymentGatewayDto.ApprovalCommand aCommand() {
        return new PaymentGatewayDto.ApprovalCommand(
                MEMBER_ID, ORDER_NUMBER, CardType.SAMSUNG, CARD_NO, Money.of(AMOUNT));
    }

    private record Fixture(PgSimulatorPaymentGateway gateway, MockRestServiceServer server) {
    }

    /** Retry-After 가 날짜로 오면 지금 시각과의 차이를 구하므로 시각을 고정한다. */
    private static final Instant NOW = Instant.parse("2026-10-04T05:00:00Z");

    private static Fixture aGatewayBackedByMockServer() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        return new Fixture(
                new PgSimulatorPaymentGateway(builder.build(), CALLBACK_URL, Clock.fixed(NOW, ZoneOffset.UTC)),
                server);
    }

    @Nested
    @DisplayName("PG가 응답을 준 경우")
    class WhenPgResponds {

        @Test
        @DisplayName("승인 요청이 접수되면 거래 식별자와 상태를 돌려주고, 우리가 정한 회원·주문·콜백 주소를 그대로 실어 보낸다")
        void returnsApproval_whenPgAcceptsRequest() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();
            String transactionKey = "20260828:TR:0a8ec1";

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andExpect(header("X-USER-ID", MEMBER_ID.toString()))
                    .andExpect(jsonPath("$.orderId").value(ORDER_NUMBER))
                    .andExpect(jsonPath("$.cardNo").value(CARD_NO))
                    .andExpect(jsonPath("$.amount").value(AMOUNT))
                    .andExpect(jsonPath("$.callbackUrl").value(CALLBACK_URL))
                    .andRespond(withSuccess("""
                            {"meta":{"result":"SUCCESS"},
                             "data":{"transactionKey":"%s","status":"PENDING","reason":null}}
                            """.formatted(transactionKey), MediaType.APPLICATION_JSON));

            // when
            PaymentGatewayDto.Approval approval = fixture.gateway().requestApproval(aCommand());

            // then
            assertAll(
                    () -> assertThat(approval.transactionKey()).isEqualTo(transactionKey),
                    () -> assertThat(approval.status()).isEqualTo(PgTransactionStatus.PENDING)
            );
        }

        @Test
        @DisplayName("PG가 400으로 거절하면 PgRejectedException이 발생한다")
        void throwsRejected_whenPgRespondsBadRequest() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            // when & then
            assertThatThrownBy(() -> fixture.gateway().requestApproval(aCommand()))
                    .isInstanceOf(PgRejectedException.class);
        }

        /**
         * 5xx는 PG가 거래를 기록하기 전에 실패했는지 후에 실패했는지 우리가 볼 수 없다.
         * 502·504는 PG 앞의 프록시가 대신 낸 것일 수도 있다. <b>거래가 있을 수 있으므로</b>
         * 결제를 확정해서는 안 된다 (참고: 07_payment.md D절).
         */
        @ParameterizedTest
        @EnumSource(value = HttpStatus.class, names = {"INTERNAL_SERVER_ERROR", "BAD_GATEWAY", "GATEWAY_TIMEOUT"})
        @DisplayName("PG가 status 로 응답하면 처리 여부를 알 수 없으므로 PgResultUnknownException이 발생하고, CoreException이 아니다")
        void throwsResultUnknown_whenPgRespondsServerError(HttpStatus status) {
            // given
            Fixture fixture = aGatewayBackedByMockServer();

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(status));

            // when
            Throwable thrown = catchThrowable(() -> fixture.gateway().requestApproval(aCommand()));

            // then
            assertAll(
                    () -> assertThat(thrown).isInstanceOf(PgResultUnknownException.class),
                    () -> assertThat(thrown).isNotInstanceOf(CoreException.class)
            );
        }

        /**
         * 2xx는 PG가 요청을 받아 거래를 만들었다는 뜻이다. 우리가 본문을 읽지 못했다고 거절로 볼 수 없다.
         */
        @Test
        @DisplayName("2xx 응답에 거래 식별자가 없으면 처리 여부를 알 수 없으므로 PgResultUnknownException이 발생한다")
        void throwsResultUnknown_whenSuccessBodyHasNoTransactionKey() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withSuccess("""
                            {"meta":{"result":"SUCCESS"},"data":null}
                            """, MediaType.APPLICATION_JSON));

            // when & then
            assertThatThrownBy(() -> fixture.gateway().requestApproval(aCommand()))
                    .isInstanceOf(PgResultUnknownException.class);
        }

        @Test
        @DisplayName("2xx 응답의 본문을 해석할 수 없으면 처리 여부를 알 수 없으므로 PgResultUnknownException이 발생한다")
        void throwsResultUnknown_whenSuccessBodyIsUnreadable() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withSuccess("<html>maintenance</html>", MediaType.APPLICATION_JSON));

            // when & then
            assertThatThrownBy(() -> fixture.gateway().requestApproval(aCommand()))
                    .isInstanceOf(PgResultUnknownException.class);
        }

        @Test
        @DisplayName("PG가 429로 수신을 거부하면 재시도할 수 있는 PgRateLimitedException이 발생하고, Retry-After 가 없으면 대기 시간도 없다")
        void throwsRateLimited_whenPgRespondsTooManyRequests() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

            // when
            Throwable thrown = catchThrowable(() -> fixture.gateway().requestApproval(aCommand()));

            // then
            assertThat(thrown).isInstanceOfSatisfying(PgRateLimitedException.class,
                    e -> assertThat(e.getRetryAfter()).isNull());
        }

        @Test
        @DisplayName("PG가 429와 Retry-After 를 초로 주면 그 시간을 예외에 담는다")
        void carriesRetryAfterSeconds_whenPgRespondsTooManyRequests() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, "2");

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

            // when
            Throwable thrown = catchThrowable(() -> fixture.gateway().requestApproval(aCommand()));

            // then
            assertThat(thrown).isInstanceOfSatisfying(PgRateLimitedException.class,
                    e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(2)));
        }

        @Test
        @DisplayName("PG가 503으로 수신을 거부하면 재시도할 수 있는 PgUnavailableException이 발생하고, Retry-After 가 없으면 대기 시간도 없다")
        void throwsUnavailable_whenPgRespondsServiceUnavailable() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

            // when
            Throwable thrown = catchThrowable(() -> fixture.gateway().requestApproval(aCommand()));

            // then
            assertThat(thrown).isInstanceOfSatisfying(PgUnavailableException.class,
                    e -> assertThat(e.getRetryAfter()).isNull());
        }

        @Test
        @DisplayName("PG가 503과 Retry-After 를 날짜로 주면 지금부터 그 시각까지를 예외에 담는다")
        void carriesRetryAfterDate_whenPgRespondsServiceUnavailable() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, "Sun, 04 Oct 2026 05:00:03 GMT");

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(headers));

            // when
            Throwable thrown = catchThrowable(() -> fixture.gateway().requestApproval(aCommand()));

            // then
            assertThat(thrown).isInstanceOfSatisfying(PgUnavailableException.class,
                    e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(3)));
        }

        @Test
        @DisplayName("Retry-After 의 날짜가 이미 지났으면 대기 시간은 0이다")
        void carriesZero_whenRetryAfterDateHasPassed() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, "Sun, 04 Oct 2026 04:59:00 GMT");

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(headers));

            // when
            Throwable thrown = catchThrowable(() -> fixture.gateway().requestApproval(aCommand()));

            // then
            assertThat(thrown).isInstanceOfSatisfying(PgUnavailableException.class,
                    e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ZERO));
        }

        /**
         * 해석하지 못한 값으로 실패를 바꾸면 재시도할 수 있는 요청을 놓친다. 값이 없는 것과 같이 다룬다.
         */
        @Test
        @DisplayName("Retry-After 를 해석할 수 없으면 대기 시간 없이 같은 예외가 발생한다")
        void carriesNoRetryAfter_whenRetryAfterIsUnreadable() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, "soon");

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

            // when
            Throwable thrown = catchThrowable(() -> fixture.gateway().requestApproval(aCommand()));

            // then
            assertThat(thrown).isInstanceOfSatisfying(PgRateLimitedException.class,
                    e -> assertThat(e.getRetryAfter()).isNull());
        }
    }

    @Nested
    @DisplayName("PG가 응답을 주지 못한 경우 — 이 둘을 가르지 못하면 Payment-010이 무너진다")
    class WhenPgDoesNotRespond {

        @Test
        @DisplayName("연결이 거부되면 요청이 나가지 못한 것이므로 재시도할 수 있는 PgConnectionFailedException이 발생한다")
        void throwsConnectionFailed_whenConnectionIsRefused() throws IOException {
            // given
            int closedPort;
            try (ServerSocket socket = new ServerSocket(0)) {
                closedPort = socket.getLocalPort();
            }
            PgSimulatorPaymentGateway gateway = new PgSimulatorPaymentGateway(
                    PgSimulatorRestClients.create("http://localhost:" + closedPort, 500, 500), CALLBACK_URL);

            // when & then
            assertThatThrownBy(() -> gateway.requestApproval(aCommand()))
                    .isInstanceOf(PgConnectionFailedException.class);
        }

        /**
         * 실제로 연결 시간 초과를 일으키는 방법(응답 없는 주소, 가득 찬 대기열)은 네트워크 환경마다 결과가 달라
         * 테스트가 흔들린다. JDK 가 연결 단계에서 던지는 예외를 그대로 재현한다. 읽기 타임아웃과 타입이 같고
         * 메시지만 다르다 — 이 둘을 가르지 못하면 처리 여부를 모르는 실패를 재시도하게 된다.
         */
        @Test
        @DisplayName("연결이 시간 안에 맺어지지 않으면 요청이 나가지 못한 것이므로 재시도할 수 있는 PgConnectionFailedException이 발생한다")
        void throwsConnectionFailed_whenConnectTimesOut() {
            // given
            Fixture fixture = aGatewayBackedByMockServer();

            fixture.server().expect(requestTo(BASE_URL + "/api/v1/payments"))
                    .andRespond(withException(new SocketTimeoutException("Connect timed out")));

            // when & then
            assertThatThrownBy(() -> fixture.gateway().requestApproval(aCommand()))
                    .isInstanceOf(PgConnectionFailedException.class);
        }

        /**
         * {@code .invalid} 는 어떤 DNS 에서도 풀리지 않도록 예약된 이름이다 (RFC 2606).
         */
        @Test
        @DisplayName("PG 주소를 찾지 못하면 요청이 나가지 못했지만 재시도하지 않는 PgHostUnresolvedException이 발생한다")
        void throwsHostUnresolved_whenHostCannotBeResolved() {
            // given
            PgSimulatorPaymentGateway gateway = new PgSimulatorPaymentGateway(
                    PgSimulatorRestClients.create("http://pg.invalid", 500, 500), CALLBACK_URL);

            // when & then
            assertThatThrownBy(() -> gateway.requestApproval(aCommand()))
                    .isInstanceOf(PgHostUnresolvedException.class);
        }

        /**
         * 연결은 맺혔고 요청도 나갔는데 응답이 없다. <b>PG가 처리했는지 알 수 없으므로</b>
         * 결제를 확정해서는 안 된다 — CoreException이 아닌 타입이어야 보상이 걸리지 않는다.
         */
        @Test
        @DisplayName("응답이 오지 않으면 처리 여부를 알 수 없으므로 PgResultUnknownException이 발생하고, CoreException이 아니다")
        void throwsResultUnknown_whenReadTimesOut() throws IOException, InterruptedException {
            // given
            ServerSocket silentServer = new ServerSocket(0);
            Thread accepting = new Thread(() -> {
                try (Socket ignored = silentServer.accept()) {
                    Thread.sleep(5_000);
                } catch (Exception ignored) {
                    // 테스트가 끝나며 소켓이 닫히는 것은 정상이다
                }
            });
            accepting.setDaemon(true);
            accepting.start();

            PgSimulatorPaymentGateway gateway = new PgSimulatorPaymentGateway(
                    PgSimulatorRestClients.create(
                            "http://localhost:" + silentServer.getLocalPort(), 1_000, 200), CALLBACK_URL);

            // when
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> gateway.requestApproval(aCommand()));

            // then
            silentServer.close();

            assertAll(
                    () -> assertThat(thrown).isInstanceOf(PgResultUnknownException.class),
                    () -> assertThat(thrown).isNotInstanceOf(com.loopers.support.error.CoreException.class)
            );
        }
    }
}
