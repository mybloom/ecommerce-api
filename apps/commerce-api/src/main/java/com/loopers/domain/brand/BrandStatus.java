package com.loopers.domain.brand;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BrandStatus {
    ACTIVE("Active", "활성", "정상적으로 노출되는 상태"),
    INACTIVE("Inactive", "비활성", "일시적으로 노출이 중단된 상태"),
    WITHDRAWN("Withdrawn", "탈퇴", "영구적으로 종료되어 더 이상 사용되지 않는 상태");

    private final String code;
    private final String label;
    private final String description;
}
