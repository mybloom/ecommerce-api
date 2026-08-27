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
                    + "포인트 결제는 이 요청 안에서 승인까지 끝나며, 실패하면 확보했던 재고가 복원되고 주문은 결제실패로 남습니다."
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
}
