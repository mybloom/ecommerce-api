package com.loopers.domain.product;

import com.loopers.domain.shared.PageQuery;
import org.jspecify.annotations.Nullable;

public class ProductServiceDto {
    public record RetrieveCommand(Long productId) {}

    public record LikeCountCommand(Long productId) {}

    public record RetrieveSummariesCommand(@Nullable Long brandId, ProductSort sort, PageQuery pageQuery) {}
}
