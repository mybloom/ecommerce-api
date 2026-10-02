package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentMethod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 목록을 손으로 채우지 않는다. 수단이 늘어도 전략 클래스만 추가하면 되도록 스프링이 주입한
 * 구현체들을 스스로 색인한다. 한 수단에 전략이 둘이면 기동 시점에 터진다.
 */
@Component
public class PaymentStrategies {

    private final Map<PaymentMethod, PaymentStrategy> byMethod;

    public PaymentStrategies(List<PaymentStrategy> strategies) {
        this.byMethod = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentStrategy::method, Function.identity()));
    }

    /**
     * 접수 <b>전에</b> 부른다. 지원하지 않는 수단으로 결제 행부터 만들면 orderId UNIQUE가
     * 그 주문의 재결제를 영구히 막는다 (참고: Payment-001).
     */
    public PaymentStrategy resolve(PaymentMethod method) {
        PaymentStrategy strategy = byMethod.get(method);

        if (strategy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 결제 수단입니다. method=" + method);
        }

        return strategy;
    }
}
