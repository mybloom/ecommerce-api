package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "Order operations")
public interface OrderV1ApiSpec {

    @Operation(
            summary = "주문 요청",
            description = "헤더 X-MEMBER-ID 로 식별된 사용자가 상품과 수량을 지정해 주문을 접수합니다. "
                    + "재고를 확보해 결제 대기 상태로 만들며, 결제는 POST /api/v1/payments 로 따로 요청합니다. "
                    + "같은 Idempotency-Key 로 다시 요청하면 새 주문을 만들지 않고 기존 주문의 현재 상태를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 접수 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 누락 또는 주문 항목이 유효하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문할 수 없는 상품 (판매중이 아니거나 브랜드가 비활성)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족")
    })
    ApiResponse<OrderV1Dto.PlaceOrderResponse> placeOrder(
            @Parameter(
                    name = "X-MEMBER-ID",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "사용자 식별자 (헤더)"
            ) Long memberId,
            @Parameter(
                    name = "Idempotency-Key",
                    required = true,
                    in = ParameterIn.HEADER,
                    description = "중복 요청 식별자 (헤더). 주문 시도마다 새로 만들어 보냅니다"
            ) String idempotencyKey,
            OrderV1Dto.PlaceOrderRequest request
    );
}
