package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentUseCase;
import com.loopers.application.payment.PaymentUseCaseDto;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@RestController
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentUseCase paymentUseCase;

    @Override
    @PostMapping
    public ApiResponse<PaymentV1Dto.PayResponse> pay(
            @RequestHeader(name = "X-MEMBER-ID") Long memberId,
            @Valid @RequestBody PaymentV1Dto.PayRequest request
    ) {
        PaymentUseCaseDto.PayResult result = paymentUseCase.pay(request.toInfo(memberId));

        return ApiResponse.success(PaymentV1Dto.PayResponse.from(result));
    }
}
