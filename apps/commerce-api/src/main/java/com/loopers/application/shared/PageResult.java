package com.loopers.application.shared;

import java.util.List;
import java.util.function.Function;

/**
 * UseCase 의 페이징 출력. interfaces 가 domain 의 PageResult 를 import 하지 않도록 레이어마다 둔다
 * (참고: architecture-rules references/paging.md).
 */
public record PageResult<T>(
        List<T> content,
        long totalCount,
        int page,
        int totalPages,
        boolean hasNext
) {
    public static <S, T> PageResult<T> from(com.loopers.domain.shared.PageResult<S> source, Function<S, T> mapper) {
        return new PageResult<>(
                source.content().stream().map(mapper).toList(),
                source.totalCount(),
                source.page(),
                source.totalPages(),
                source.hasNext()
        );
    }
}
