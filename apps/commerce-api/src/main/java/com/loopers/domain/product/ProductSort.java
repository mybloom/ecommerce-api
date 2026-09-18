package com.loopers.domain.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductSort {
    LATEST("개시 일시(openedAt) 내림차순. 등록 시각이 아니라 개시 시각이 사용자에게 보이는 신상품 순서다"),
    PRICE_DESC("판매 가격 내림차순"),
    LIKE_DESC("좋아요 수 내림차순. 매번 세지 않고 캐시 컬럼을 쓴다 (참고: Product-003)");

    private final String description;
}
