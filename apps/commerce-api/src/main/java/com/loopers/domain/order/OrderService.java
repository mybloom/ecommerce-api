package com.loopers.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 3단계(Domain Layer)에서 본문을 채운다. 지금은 application 레이어가 부르는 계약만 있다.
 */
@RequiredArgsConstructor
@Service
public class OrderService {

    /**
     * 같은 키로 접수된 주문을 찾는다. 있는 것도 없는 것도 정상이라 예외 대신 Optional로 돌려준다.
     * 있으면 중복 요청이고 없으면 첫 요청인데, 두 경우의 트랜잭션 경계가 달라 판단은 UseCase가 한다 (참고: Order-004).
     */
    @Transactional(readOnly = true)
    public Optional<Order> findByIdempotencyKey(OrderServiceDto.FindByIdempotencyKeyCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    /**
     * 주문번호를 채번해 접수 상태로 저장한다 (참고: Order-011은 접수 시점에 채번하도록 정한다).
     */
    @Transactional
    public Order draft(OrderServiceDto.DraftCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    @Transactional
    public Order confirm(OrderServiceDto.ConfirmCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    @Transactional
    public Order markOrderFailed(OrderServiceDto.MarkOrderFailedCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
    }
}
