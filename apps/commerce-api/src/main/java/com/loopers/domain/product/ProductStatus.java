package com.loopers.domain.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    ON_SALE("OnSale", "판매중", "사용자에게 노출됨"),
    OFF_SALE("OffSale", "판매중지", "노출되지 않음"),
    HIDDEN("Hidden", "숨김", "노출되지 않음, 운영상 사유");

    private final String code;
    private final String label;
    private final String description;

    public boolean isVisibleToUser() {
        return this == ON_SALE;
    }
}
