package com.loopers.domain.product;

import com.loopers.domain.shared.PageQuery;
import com.loopers.domain.shared.PageResult;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);

    Optional<Product> findByIdForUpdate(Long id);

    PageResult<ProductSummary> findOnSaleSummaries(@Nullable Long brandId, ProductSort sort, PageQuery pageQuery);
}
