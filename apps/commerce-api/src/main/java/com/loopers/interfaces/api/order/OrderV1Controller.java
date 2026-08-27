package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderUseCase;
import com.loopers.application.order.OrderUseCaseDto;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@RestController
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderUseCase orderUseCase;

    @Override
    @PostMapping
    public ApiResponse<OrderV1Dto.PlaceOrderResponse> placeOrder(
            @RequestHeader(name = "X-MEMBER-ID") Long memberId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderV1Dto.PlaceOrderRequest request
    ) {
        OrderUseCaseDto.PlaceOrderResult result = orderUseCase.place(request.toInfo(memberId, idempotencyKey));

        return ApiResponse.success(OrderV1Dto.PlaceOrderResponse.from(result));
    }
}
