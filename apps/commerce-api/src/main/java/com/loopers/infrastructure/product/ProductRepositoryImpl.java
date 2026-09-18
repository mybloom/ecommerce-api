package com.loopers.infrastructure.product;

import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.brand.QBrand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.product.ProductSummary;
import com.loopers.domain.product.QProduct;
import com.loopers.domain.product.StockQuantity;
import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.PageQuery;
import com.loopers.domain.shared.PageResult;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private static final QProduct PRODUCT = QProduct.product;
    private static final QBrand BRAND = QBrand.brand;

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Optional<Product> findByIdForUpdate(Long id) {
        return productJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public PageResult<ProductSummary> findOnSaleSummaries(@Nullable Long brandId, ProductSort sort, PageQuery pageQuery) {
        List<ProductSummary> content = queryFactory
                .select(PRODUCT.id, PRODUCT.name, PRODUCT.representativeImage,
                        PRODUCT.brandId, BRAND.name, PRODUCT.price.amount,
                        PRODUCT.likeCount, PRODUCT.stockQuantity.value)
                .from(PRODUCT)
                .join(BRAND).on(BRAND.id.eq(PRODUCT.brandId))
                .where(onSale(brandId))
                .orderBy(orderOf(sort), PRODUCT.id.desc())
                .offset(pageQuery.offset())
                .limit(pageQuery.size())
                .fetch()
                .stream()
                .map(ProductRepositoryImpl::toSummary)
                .toList();

        Long totalCount = queryFactory
                .select(PRODUCT.count())
                .from(PRODUCT)
                .join(BRAND).on(BRAND.id.eq(PRODUCT.brandId))
                .where(onSale(brandId))
                .fetchOne();

        return new PageResult<>(content, totalCount == null ? 0L : totalCount, pageQuery.page(), pageQuery.size());
    }

    private static BooleanExpression onSale(@Nullable Long brandId) {
        BooleanExpression onSale = PRODUCT.status.eq(ProductStatus.ON_SALE)
                .and(BRAND.status.eq(BrandStatus.ACTIVE));

        return brandId == null ? onSale : onSale.and(PRODUCT.brandId.eq(brandId));
    }

    private static OrderSpecifier<?> orderOf(ProductSort sort) {
        return switch (sort) {
            case LATEST -> PRODUCT.openedAt.desc();
            case PRICE_DESC -> PRODUCT.price.amount.desc();
            case LIKE_DESC -> PRODUCT.likeCount.desc();
        };
    }

    private static ProductSummary toSummary(Tuple tuple) {
        return new ProductSummary(
                requireNonNull(tuple.get(PRODUCT.id)),
                requireNonNull(tuple.get(PRODUCT.name)),
                requireNonNull(tuple.get(PRODUCT.representativeImage)),
                requireNonNull(tuple.get(PRODUCT.brandId)),
                requireNonNull(tuple.get(BRAND.name)),
                Money.of(requireNonNull(tuple.get(PRODUCT.price.amount))),
                requireNonNull(tuple.get(PRODUCT.likeCount)),
                StockQuantity.of(requireNonNull(tuple.get(PRODUCT.stockQuantity.value)))
        );
    }
}
