package com.loopers.interfaces.api;

import com.loopers.application.shared.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, PageMeta page) {

    public record PageMeta(long totalCount, int page, int totalPages, boolean hasNext) {
    }

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(
                result.content().stream().map(mapper).toList(),
                new PageMeta(result.totalCount(), result.page(), result.totalPages(), result.hasNext())
        );
    }
}
