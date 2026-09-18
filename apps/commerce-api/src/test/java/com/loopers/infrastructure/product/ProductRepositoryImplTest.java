package com.loopers.infrastructure.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.product.ProductSummary;
import com.loopers.domain.shared.PageQuery;
import com.loopers.domain.shared.PageResult;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductRepositoryImplTest {

    private static final int DEFAULT_SIZE = 20;
    private static final int FIRST_PAGE = 0;

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductRepositoryImplTest(ProductRepository productRepository, BrandRepository brandRepository,
                                     DatabaseCleanUp databaseCleanUp) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("findOnSaleSummaries")
    class FindOnSaleSummaries {

        @Test
        @DisplayName("상품 요약에는 상품 정보와 소속 브랜드 이름이 담긴다")
        void returnsProductWithBrandName() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LATEST, FIRST_PAGE, DEFAULT_SIZE);

            // then
            ProductSummary summary = result.content().get(0);
            assertAll(
                    () -> assertThat(summary.productId()).isEqualTo(savedProduct.getId()),
                    () -> assertThat(summary.name()).isEqualTo(ProductFixture.DEFAULT_NAME),
                    () -> assertThat(summary.representativeImage()).isEqualTo(ProductFixture.DEFAULT_IMAGE),
                    () -> assertThat(summary.brandId()).isEqualTo(savedBrand.getId()),
                    () -> assertThat(summary.brandName()).isEqualTo(BrandFixture.DEFAULT_NAME),
                    () -> assertThat(summary.price()).isEqualTo(ProductFixture.DEFAULT_PRICE),
                    () -> assertThat(summary.likeCount()).isZero(),
                    () -> assertThat(summary.isSoldOut()).isFalse()
            );
        }

        @Test
        @DisplayName("size보다 상품이 많으면 size만큼만 담고 전체 개수는 조건에 맞는 전부를 센다")
        void limitsContentToSize_andCountsAll() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            int size = 2;
            long expectedTotalCount = 3L;

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LATEST, FIRST_PAGE, size);

            // then
            assertAll(
                    () -> assertThat(result.content()).hasSize(size),
                    () -> assertThat(result.totalCount()).isEqualTo(expectedTotalCount)
            );
        }

        @Test
        @DisplayName("마지막 페이지를 넘은 page는 빈 목록이지만 전체 개수는 그대로다")
        void returnsEmptyContentWithTotalCount_whenPageIsBeyondLast() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            int beyondLastPage = 5;
            long expectedTotalCount = 1L;

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LATEST, beyondLastPage, DEFAULT_SIZE);

            // then
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.totalCount()).isEqualTo(expectedTotalCount)
            );
        }

        @Test
        @DisplayName("판매 중지·숨김 상품은 목록과 전체 개수에서 모두 빠진다")
        void excludesNotOnSaleProducts() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product onSaleProduct = productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            productRepository.save(ProductFixture.aProductForBrandWithStatus(savedBrand.getId(), ProductStatus.OFF_SALE));
            productRepository.save(ProductFixture.aProductForBrandWithStatus(savedBrand.getId(), ProductStatus.HIDDEN));
            long expectedTotalCount = 1L;

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LATEST, FIRST_PAGE, DEFAULT_SIZE);

            // then
            assertAll(
                    () -> assertThat(result.content()).extracting(ProductSummary::productId)
                            .containsExactly(onSaleProduct.getId()),
                    () -> assertThat(result.totalCount()).isEqualTo(expectedTotalCount)
            );
        }

        @Test
        @DisplayName("ACTIVE가 아닌 브랜드의 상품은 목록과 전체 개수에서 모두 빠진다")
        void excludesProductsOfInactiveBrands() {
            // given
            Brand activeBrand = brandRepository.save(BrandFixture.aBrand());
            Brand inactiveBrand = brandRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));
            Product productOfActiveBrand = productRepository.save(ProductFixture.aProductForBrand(activeBrand.getId()));
            productRepository.save(ProductFixture.aProductForBrand(inactiveBrand.getId()));
            long expectedTotalCount = 1L;

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LATEST, FIRST_PAGE, DEFAULT_SIZE);

            // then
            assertAll(
                    () -> assertThat(result.content()).extracting(ProductSummary::productId)
                            .containsExactly(productOfActiveBrand.getId()),
                    () -> assertThat(result.totalCount()).isEqualTo(expectedTotalCount)
            );
        }

        @Test
        @DisplayName("품절 상품도 목록에 담기고 품절로 표시된다")
        void includesSoldOutProducts() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int noStock = 0;
            Product soldOutProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStock(savedBrand.getId(), noStock));

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LATEST, FIRST_PAGE, DEFAULT_SIZE);

            // then
            ProductSummary summary = result.content().get(0);
            assertAll(
                    () -> assertThat(summary.productId()).isEqualTo(soldOutProduct.getId()),
                    () -> assertThat(summary.isSoldOut()).isTrue()
            );
        }

        @Test
        @DisplayName("brandId를 주면 그 브랜드의 상품만 담고 전체 개수도 그 브랜드만 센다")
        void filtersByBrandId() {
            // given
            Brand targetBrand = brandRepository.save(BrandFixture.aBrand());
            Brand otherBrand = brandRepository.save(BrandFixture.aBrand());
            Product targetProduct = productRepository.save(ProductFixture.aProductForBrand(targetBrand.getId()));
            productRepository.save(ProductFixture.aProductForBrand(otherBrand.getId()));
            long expectedTotalCount = 1L;

            // when
            PageResult<ProductSummary> result =
                    findSummaries(targetBrand.getId(), ProductSort.LATEST, FIRST_PAGE, DEFAULT_SIZE);

            // then
            assertAll(
                    () -> assertThat(result.content()).extracting(ProductSummary::productId)
                            .containsExactly(targetProduct.getId()),
                    () -> assertThat(result.totalCount()).isEqualTo(expectedTotalCount)
            );
        }

        @Test
        @DisplayName("LATEST는 개시 일시가 늦은 상품부터 담는다")
        void sortsByOpenedAtDesc_whenSortIsLatest() {
            // given
            ZonedDateTime now = ZonedDateTime.now();
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product olderProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithOpenedAt(savedBrand.getId(), now.minusDays(2)));
            Product newerProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithOpenedAt(savedBrand.getId(), now.minusDays(1)));

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LATEST, FIRST_PAGE, DEFAULT_SIZE);

            // then
            assertThat(result.content()).extracting(ProductSummary::productId)
                    .containsExactly(newerProduct.getId(), olderProduct.getId());
        }

        @Test
        @DisplayName("PRICE_DESC는 가격이 높은 상품부터 담는다")
        void sortsByPriceDesc_whenSortIsPriceDesc() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product cheapProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithPrice(savedBrand.getId(), 10_000L));
            Product expensiveProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithPrice(savedBrand.getId(), 50_000L));

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.PRICE_DESC, FIRST_PAGE, DEFAULT_SIZE);

            // then
            assertThat(result.content()).extracting(ProductSummary::productId)
                    .containsExactly(expensiveProduct.getId(), cheapProduct.getId());
        }

        @Test
        @DisplayName("LIKE_DESC는 좋아요가 많은 상품부터 담는다")
        void sortsByLikeCountDesc_whenSortIsLikeDesc() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product lessLikedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), 1));
            Product mostLikedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), 5));

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LIKE_DESC, FIRST_PAGE, DEFAULT_SIZE);

            // then
            assertThat(result.content()).extracting(ProductSummary::productId)
                    .containsExactly(mostLikedProduct.getId(), lessLikedProduct.getId());
        }

        @Test
        @DisplayName("정렬 기준 값이 같으면 상품 id가 큰 상품부터 담아 순서가 흔들리지 않는다")
        void sortsByIdDesc_whenSortValuesAreEqual() {
            // given
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int sameLikeCount = 3;
            Product earlierProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), sameLikeCount));
            Product laterProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), sameLikeCount));

            // when
            PageResult<ProductSummary> result = findSummaries(null, ProductSort.LIKE_DESC, FIRST_PAGE, DEFAULT_SIZE);

            // then
            assertThat(result.content()).extracting(ProductSummary::productId)
                    .containsExactly(laterProduct.getId(), earlierProduct.getId());
        }

        private PageResult<ProductSummary> findSummaries(Long brandId, ProductSort sort, int page, int size) {
            return productRepository.findOnSaleSummaries(brandId, sort, new PageQuery(page, size));
        }
    }
}
