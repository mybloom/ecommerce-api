package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PgResultUnknownException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

@Slf4j
@RequiredArgsConstructor
@Component
public class CardPaymentStrategy implements PaymentStrategy {

    private final PaymentGateway paymentGateway;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    /**
     * <b>트랜잭션을 갖지 않는다.</b> PG 왕복 동안 DB 커넥션을 쥐면 안 되고, 접수가 커밋된 뒤라야
     * 먼저 도착한 콜백이 볼 것이 있다 (참고: Payment-005).
     * <p>
     * 승인 결과가 아니라 접수까지만 확인하고 <b>PENDING 그대로 돌려준다.</b> 최종 결과는 콜백이
     * 정하며 주문은 AWAITING_PAYMENT에 머문다 (참고: Payment-003).
     * <p>
     * 실패는 게이트웨이가 세 타입으로 번역해 던진다. <b>확정할 수 있는 실패는 잡지 않는다</b> —
     * 보상을 걸지 말지는 예외 타입이 정하고, 호출자의 {@code catch (CoreException)}이 집행한다
     * (참고: Payment-010).
     * <p>
     * PG가 처리했는지 모르는 실패만 여기서 받아 <b>PENDING으로 돌려준다.</b> 결제 접수 자체는 됐으므로
     * 오류로 응답할 일이 아니며, 정상 접수와 같은 형태다. 최종 결과는 콜백이 정한다.
     */
    @Override
    public Payment approve(Payment accepted, PaymentUseCaseDto.PayInfo info) {
        // 카드 정보의 유무는 요청 단계에서 이미 걸렀다 (PayRequest.isCardInfoPresent)
        CardType cardType = CardType.valueOf(requireNonNull(info.cardType(), "카드 종류는 필수입니다.").name());
        String cardNo = requireNonNull(info.cardNo(), "카드 번호는 필수입니다.");

        try {
            // 반환된 transactionKey는 저장하지 않는다. 승인된 뒤에만 존재해야 하는 값이다 (참고: B.1 불변식)
            paymentGateway.requestApproval(new PaymentGatewayDto.ApprovalCommand(
                    info.memberId(),
                    info.orderNumber(),
                    cardType,
                    cardNo,
                    accepted.getAmount()));
        } catch (PgResultUnknownException e) {
            log.warn("PG 처리 여부를 알 수 없어 결제를 PENDING으로 둡니다. orderNumber={}", info.orderNumber(), e);
        }

        return accepted;
    }
}
