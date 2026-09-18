package com.loopers.domain.shared;

public record PageQuery(int page, int size) {

    public long offset() {
        throw new UnsupportedOperationException("3단계에서 구현");
    }
}
