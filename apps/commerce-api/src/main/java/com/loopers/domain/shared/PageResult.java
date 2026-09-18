package com.loopers.domain.shared;

import java.util.List;

/**
 * 페이징 조회 결과. 전체 페이지 수와 다음 페이지 여부는 여기서만 계산한다
 * (참고: architecture-rules references/paging.md 4절).
 */
public record PageResult<T>(List<T> content, long totalCount, int page, int size) {

    public int totalPages() {
        return (int) ((totalCount + size - 1) / size);
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }
}
