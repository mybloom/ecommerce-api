package com.loopers.domain.shared;

/**
 * 페이징 요청. 범위를 벗어난 값은 거절하지 않고 여기서만 보정한다 (참고: architecture-rules references/paging.md 2절).
 */
public record PageQuery(int page, int size) {
    public static final int MAX_SIZE = 100;

    public PageQuery {
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public long offset() {
        return (long) page * size;
    }
}
