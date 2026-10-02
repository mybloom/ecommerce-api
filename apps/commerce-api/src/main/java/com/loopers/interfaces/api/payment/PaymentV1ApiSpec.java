package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "Payment operations")
public interface PaymentV1ApiSpec {

    @Operation(
            summary = "결제 요청",
            description = "헤더 X-MEMBER-ID 로 식별된 사용자가 결제 대기 상태의 주문을 결제합니다. "
                    + "결제 금액은 요청에 담지 않고 주문의 총액을 그대로 사용합니다. "
                    + "포인트 결제는 이 요청 안에서 승인까지 끝나며, 실패하면 확보했던 재고가 복원되고 주문은 결제실패로 남습니다. "
                    + "카드 결제는 승인대기 상태로 응답하며 최종 결과는 PG 콜백이 정합니다 — 이 응답의 status 가 PENDING 인 것은 결제 성공을 뜻하지 않습니다. "
                    + "카드 결제일 때는 cardType 과 cardNo 가 필수입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "결제 승인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 누락 또는 요청 값이 유효하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음 (다른 회원의 주문 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "결제할 수 없는 주문이거나 포인트 잔액 부족")
    })
    ApiResponse<PaymentV1Dto.PayResponse> pay(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId,
            PaymentV1Dto.PayRequest request
    );

    @Operation(
            summary = "PG 결제 결과 수신",
            description = "PG 가 카드 승인 결과를 알려오면 결제와 주문을 종결합니다. "
                    + "호출자가 PG 이므로 X-MEMBER-ID 를 받지 않고 소유권도 검사하지 않으며, 대상은 주문번호로만 특정합니다. "
                    + "이미 종결된 결제에 두 번째 결과가 오면 아무것도 하지 않습니다. "
                    + "처리에 실패하면 500 으로 응답해 PG 의 재전송을 유도합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수신 확인. 본문은 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 유효하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문번호에 해당하는 카드 결제가 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "처리 실패. PG 의 재전송을 유도한다")
    })
    ApiResponse<Object> handleCallback(PaymentV1Dto.PgCallbackRequest request);
}
