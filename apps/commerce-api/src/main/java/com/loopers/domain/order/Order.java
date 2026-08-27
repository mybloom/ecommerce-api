package com.loopers.domain.order;

import com.loopers.domain.shared.Money;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * 3단계(Domain Layer)에서 채운다.
 * 지금은 application 레이어의 시그니처가 참조하는 타입으로만 존재한다 — 불변식도 행위도 아직 없다.
 */
@Getter
public class Order {

    private Long id;
    private OrderNumber orderNumber;
    private OrderStatus status;
    private Money totalAmount;
    private ZonedDateTime orderedAt;
}
