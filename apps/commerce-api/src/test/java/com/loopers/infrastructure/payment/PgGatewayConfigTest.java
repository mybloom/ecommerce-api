package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 재시도는 PG 어댑터를 감싸는 데코레이터에 두고, 조립은 이 설정 한곳에서 한다 (참고: 07_payment.md E.4 정한 것 2번).
 */
class PgGatewayConfigTest {

    private static final String BASE_URL = "http://localhost:8082";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/pg/callback";
    private static final int CONNECT_TIMEOUT_MILLIS = 1_000;
    private static final int READ_TIMEOUT_MILLIS = 3_000;

    /** 첫 시도 + 재시도 1회 */
    private static final int MAX_ATTEMPTS = 2;
    private static final long CONNECTION_FAILED_MAX_WAIT_MILLIS = 200;
    private static final long RATE_LIMITED_MIN_WAIT_MILLIS = 500;
    private static final long RATE_LIMITED_MAX_WAIT_MILLIS = 700;
    private static final long UNAVAILABLE_MIN_WAIT_MILLIS = 300;
    private static final long UNAVAILABLE_MAX_WAIT_MILLIS = 500;
    private static final long RETRY_AFTER_CAP_MILLIS = 1_000;

    private static final int SLIDING_WINDOW_SIZE = 20;
    private static final int MINIMUM_NUMBER_OF_CALLS = 10;
    private static final float FAILURE_RATE_THRESHOLD = 50;
    private static final long WAIT_DURATION_IN_OPEN_STATE_MILLIS = 10_000;
    private static final int PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE = 3;

    private static final PgProperties PROPERTIES = new PgProperties(
            BASE_URL,
            CALLBACK_URL,
            CONNECT_TIMEOUT_MILLIS,
            READ_TIMEOUT_MILLIS,
            new PgProperties.RetryPolicy(
                    MAX_ATTEMPTS,
                    CONNECTION_FAILED_MAX_WAIT_MILLIS,
                    RATE_LIMITED_MIN_WAIT_MILLIS,
                    RATE_LIMITED_MAX_WAIT_MILLIS,
                    UNAVAILABLE_MIN_WAIT_MILLIS,
                    UNAVAILABLE_MAX_WAIT_MILLIS,
                    RETRY_AFTER_CAP_MILLIS),
            new PgProperties.CircuitBreakerPolicy(
                    SLIDING_WINDOW_SIZE,
                    MINIMUM_NUMBER_OF_CALLS,
                    FAILURE_RATE_THRESHOLD,
                    WAIT_DURATION_IN_OPEN_STATE_MILLIS,
                    PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE));

    @Test
    @DisplayName("PaymentGateway 빈은 PG 어댑터를 재시도 데코레이터로 감싼 것이다")
    void wrapsAdapterWithRetry() {
        // when
        PaymentGateway gateway = new PgGatewayConfig().paymentGateway(PROPERTIES);

        // then
        assertThat(gateway).isInstanceOf(RetryingPaymentGateway.class);
    }

    /**
     * 두 타임아웃은 같은 int 라 순서가 뒤바뀌어도 컴파일된다. 뒤바뀌면 읽기 타임아웃이 연결 타임아웃 값으로 바뀐다.
     * 연결은 맺어 주고 응답하지 않는 서버로, 읽기 타임아웃이 설정한 값대로 걸리는지 본다.
     */
    @Test
    @DisplayName("readTimeoutMillis 가 connectTimeoutMillis 보다 짧을 때 응답이 없으면 readTimeoutMillis 만에 처리 여부를 모르는 실패로 끝난다")
    void appliesReadTimeout_asConfigured() throws IOException {
        // given
        int shortReadTimeoutMillis = 100;
        int longConnectTimeoutMillis = 3_000;

        try (ServerSocket silentServer = new ServerSocket(0)) {
            Thread accepting = new Thread(() -> {
                try (Socket ignored = silentServer.accept()) {
                    Thread.sleep(5_000);
                } catch (Exception ignored) {
                    // 테스트가 끝나며 소켓이 닫히는 것은 정상이다
                }
            });
            accepting.setDaemon(true);
            accepting.start();

            PgProperties properties = new PgProperties(
                    "http://localhost:" + silentServer.getLocalPort(),
                    CALLBACK_URL,
                    longConnectTimeoutMillis,
                    shortReadTimeoutMillis,
                    PROPERTIES.retry(),
                    PROPERTIES.circuitBreaker());
            PaymentGateway gateway = new PgGatewayConfig().paymentGateway(properties);
            PaymentGatewayDto.ApprovalCommand command = new PaymentGatewayDto.ApprovalCommand(
                    135135L, "20260828-A3F9K2QP", CardType.SAMSUNG, "1234-5678-9814-1451", Money.of(5_000L));

            // when
            long startedAt = System.nanoTime();
            Throwable thrown = catchThrowable(() -> gateway.requestApproval(command));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            // then
            assertAll(
                    () -> assertThat(thrown).isInstanceOf(PgResultUnknownException.class),
                    () -> assertThat(elapsed).isLessThan(Duration.ofMillis(longConnectTimeoutMillis))
            );
        }
    }
}
