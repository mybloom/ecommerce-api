package com.loopers.infrastructure.order;

import com.loopers.domain.order.IdempotencyKey;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderNumber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdempotencyKey(IdempotencyKey idempotencyKey);
    Optional<Order> findByOrderNumber(OrderNumber orderNumber);
}
