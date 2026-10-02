package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PgRejectedException;
import com.loopers.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 실제 pg-simulator(8082)를 부른다. <b>스텁이 통과시키는 요청 형식을 여기서만 검증할 수 있다</b> —
 * cardNo 정규식, callbackUrl prefix, orderId 길이는 PG만 알고 있다 (참고: 03_외부시스템_테스트전략.md).
 * <p>
 * <b>시뮬레이터가 40% 확률로 거절하므로 이 테스트는 실패할 수 있다.</b> 그것이 정상 동작이며,
 * 여기서 확인하려는 것은 "우리 요청이 형식 때문에 거절되지는 않는가"다.
 * <p>
 * 실행: {@code ./gradlew :apps:commerce-api:externalTest}
 */
@Tag("external")
class PgSimulatorPaymentGatewayExternalTest {

    private static final String BASE_URL = "http://localhost:8082";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/pg/callback";
    private static final int ATTEMPTS = 10;

    private static PgSimulatorPaymentGateway aGateway() {
        return new PgSimulatorPaymentGateway(
                PgSimulatorRestClients.create(BASE_URL, 1_000, 3_000), CALLBACK_URL);
    }

    private static String anOrderNumber() {
        return ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-EXT" + System.nanoTime() % 100000;
    }

    /**
     * 형식 오류는 매번 거절되지만 랜덤 거절은 40%다. 여러 번 쏴서 한 번이라도 접수되면
     * 형식은 문제가 없다는 뜻이다.
     */
    @Test
    @DisplayName("우리가 만든 승인 요청을 PG가 형식 문제로 거절하지 않는다")
    void acceptsOurApprovalRequest() {
        // given
        PgSimulatorPaymentGateway gateway = aGateway();
        PaymentGatewayDto.Approval accepted = null;
        Throwable lastFailure = null;

        // when
        for (int i = 0; i < ATTEMPTS && accepted == null; i++) {
            PaymentGatewayDto.ApprovalCommand command = new PaymentGatewayDto.ApprovalCommand(
                    135135L, anOrderNumber(), CardType.SAMSUNG, "1234-5678-9814-1451", Money.of(5_000L));

            try {
                accepted = gateway.requestApproval(command);
            } catch (Throwable t) {
                lastFailure = t;
            }
        }

        // then
        assertThat(accepted)
                .withFailMessage("%d번 모두 거절되었습니다. 마지막 실패: %s", ATTEMPTS, lastFailure)
                .isNotNull();
        assertThat(accepted.transactionKey()).isNotBlank();
    }

    @Test
    @DisplayName("카드 번호 형식이 어긋나면 PG가 거절해 PgRejectedException이 발생한다")
    void throwsRejected_whenCardNoFormatIsInvalid() {
        // given
        PgSimulatorPaymentGateway gateway = aGateway();
        String malformedCardNo = "1234-5678";

        PaymentGatewayDto.ApprovalCommand command = new PaymentGatewayDto.ApprovalCommand(
                135135L, anOrderNumber(), CardType.SAMSUNG, malformedCardNo, Money.of(5_000L));

        // when
        Throwable thrown = catchThrowable(() -> gateway.requestApproval(command));

        // then
        assertThat(thrown).isInstanceOf(PgRejectedException.class);
    }
}
