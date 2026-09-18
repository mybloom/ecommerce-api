package com.loopers.domain.shared;

import java.util.List;

public record PageResult<T>(List<T> content, long totalCount, int page, int size) {

    public int totalPages() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }

    public boolean hasNext() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }
}
