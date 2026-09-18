package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.shared.Money;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.productlike.ProductLikeJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductLikeJpaRepository productLikeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, BrandJpaRepository brandJpaRepository,
                               ProductJpaRepository productJpaRepository,
                               ProductLikeJpaRepository productLikeJpaRepository, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.productLikeJpaRepository = productLikeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("GET /api/v1/products/{productId}")
    class GetProduct {
        private static final String ENDPOINT = "/api/v1/products/{productId}";

        @Test
        @DisplayName("ON_SALE 상품을 조회하면 200과 상품/브랜드 정보를 반환한다")
        void returnsProductInfo_whenProductIsOnSale() {
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product savedProduct = productJpaRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.GetProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.GetProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().productId()).isEqualTo(savedProduct.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(ProductFixture.DEFAULT_NAME),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(savedBrand.getId()),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo(BrandFixture.DEFAULT_NAME)
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품을 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenProductDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.GetProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.GetProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, Long.MAX_VALUE);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @Test
        @DisplayName("OFF_SALE 상품을 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenProductIsOffSale() {
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product savedProduct = productJpaRepository.save(
                    ProductFixture.aProductForBrandWithStatus(savedBrand.getId(), ProductStatus.OFF_SALE));

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.GetProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.GetProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @Test
        @DisplayName("소속 브랜드가 INACTIVE인 상품을 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenBrandIsInactive() {
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));
            Product savedProduct = productJpaRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));

            ParameterizedTypeReference<ApiResponse<ProductV1Dto.GetProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.GetProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class GetProducts {
        private static final String ENDPOINT = "/api/v1/products";
        private static final ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> RESPONSE_TYPE =
                new ParameterizedTypeReference<>() {};

        @Test
        @DisplayName("sort 없이 요청하면 최근에 개시한 상품부터 나온다")
        void returnsLatestFirst_whenSortIsOmitted() {
            // given
            ZonedDateTime now = ZonedDateTime.now();
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product olderProduct = productJpaRepository.save(aProduct(savedBrand.getId(), 10_000L, now.minusDays(2)));
            Product newerProduct = productJpaRepository.save(aProduct(savedBrand.getId(), 10_000L, now.minusDays(1)));

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE);

            // then
            assertThat(response.getBody().data().content()).extracting(ProductV1Dto.ProductSummaryResponse::productId)
                    .containsExactly(newerProduct.getId(), olderProduct.getId());
        }

        @Test
        @DisplayName("목록의 각 상품에는 저장된 상품 정보와 소속 브랜드 이름이 그대로 담긴다")
        void returnsStoredProductAndBrandName() {
            // given
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            int likeCount = 3;
            Product savedProduct = productJpaRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), likeCount));

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE);

            // then
            ProductV1Dto.ProductSummaryResponse summary = response.getBody().data().content().get(0);
            assertAll(
                    () -> assertThat(summary.productId()).isEqualTo(savedProduct.getId()),
                    () -> assertThat(summary.name()).isEqualTo(ProductFixture.DEFAULT_NAME),
                    () -> assertThat(summary.representativeImage()).isEqualTo(ProductFixture.DEFAULT_IMAGE),
                    () -> assertThat(summary.brandId()).isEqualTo(savedBrand.getId()),
                    () -> assertThat(summary.brandName()).isEqualTo(BrandFixture.DEFAULT_NAME),
                    () -> assertThat(summary.price()).isEqualTo(ProductFixture.DEFAULT_PRICE.getAmount()),
                    () -> assertThat(summary.likeCount()).isEqualTo(likeCount),
                    () -> assertThat(summary.isSoldOut()).isFalse()
            );
        }

        @Test
        @DisplayName("sort=priceDesc 이면 가격 높은순으로 반환한다")
        void returnsByPriceDesc_whenSortIsPriceDesc() {
            // given
            ZonedDateTime openedAt = ZonedDateTime.now().minusDays(1);
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product cheapProduct = productJpaRepository.save(aProduct(savedBrand.getId(), 10_000L, openedAt));
            Product expensiveProduct = productJpaRepository.save(aProduct(savedBrand.getId(), 50_000L, openedAt));
            String sort = "priceDesc";

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?sort={sort}", HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE, sort);

            // then
            assertThat(response.getBody().data().content()).extracting(ProductV1Dto.ProductSummaryResponse::productId)
                    .containsExactly(expensiveProduct.getId(), cheapProduct.getId());
        }

        @Test
        @DisplayName("sort=likeDesc 이면 좋아요 많은순으로 반환한다")
        void returnsByLikeDesc_whenSortIsLikeDesc() {
            // given
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product lessLikedProduct = productJpaRepository.save(ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), 1));
            Product mostLikedProduct = productJpaRepository.save(ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), 5));
            String sort = "likeDesc";

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?sort={sort}", HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE, sort);

            // then
            assertThat(response.getBody().data().content()).extracting(ProductV1Dto.ProductSummaryResponse::productId)
                    .containsExactly(mostLikedProduct.getId(), lessLikedProduct.getId());
        }

        @Test
        @DisplayName("sort가 목록에 없는 값이면 200이고 개시 일시 최신순으로 반환한다")
        void returnsByLatest_whenSortIsUnknown() {
            // given
            ZonedDateTime now = ZonedDateTime.now();
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product olderExpensiveProduct = productJpaRepository.save(aProduct(savedBrand.getId(), 50_000L, now.minusDays(2)));
            Product newerCheapProduct = productJpaRepository.save(aProduct(savedBrand.getId(), 10_000L, now.minusDays(1)));
            String unknownSort = "priceAsc";

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?sort={sort}", HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE, unknownSort);

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).extracting(ProductV1Dto.ProductSummaryResponse::productId)
                            .containsExactly(newerCheapProduct.getId(), olderExpensiveProduct.getId())
            );
        }

        @Test
        @DisplayName("brandId를 주면 그 브랜드의 상품만 반환한다")
        void returnsOnlyBrandProducts_whenBrandIdIsGiven() {
            // given
            Brand targetBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Brand otherBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product targetProduct = productJpaRepository.save(ProductFixture.aProductForBrand(targetBrand.getId()));
            productJpaRepository.save(ProductFixture.aProductForBrand(otherBrand.getId()));

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?brandId={brandId}", HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE, targetBrand.getId());

            // then
            assertThat(response.getBody().data().content()).extracting(ProductV1Dto.ProductSummaryResponse::productId)
                    .containsExactly(targetProduct.getId());
        }

        @Test
        @DisplayName("비활성 브랜드의 brandId로 조회해도 404가 아니라 200과 빈 목록·전체 개수 0을 응답한다")
        void returnsEmpty_whenBrandIsInactive() {
            // given
            Brand inactiveBrand = brandJpaRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));
            productJpaRepository.save(ProductFixture.aProductForBrand(inactiveBrand.getId()));

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?brandId={brandId}", HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE, inactiveBrand.getId());

            // then
            PageResponse<ProductV1Dto.ProductSummaryResponse> body = response.getBody().data();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(body.content()).isEmpty(),
                    () -> assertThat(body.page().totalCount()).isZero()
            );
        }

        @Test
        @DisplayName("상품이 3개이고 size가 2일 때 두 번째 페이지를 요청하면 1개가 나오고, 전체 개수 3·전체 페이지 수 2·다음 페이지 없음을 응답한다")
        void returnsLastPartialPage_whenSecondPageIsRequested() {
            // given
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            productJpaRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            productJpaRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            productJpaRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            int page = 1;
            int size = 2;

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page={page}&size={size}", HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE, page, size);

            // then
            PageResponse<ProductV1Dto.ProductSummaryResponse> body = response.getBody().data();
            assertAll(
                    () -> assertThat(body.content()).hasSize(1),
                    () -> assertThat(body.page().totalCount()).isEqualTo(3L),
                    () -> assertThat(body.page().page()).isEqualTo(1),
                    () -> assertThat(body.page().totalPages()).isEqualTo(2),
                    () -> assertThat(body.page().hasNext()).isFalse()
            );
        }

        @Test
        @DisplayName("마지막 페이지를 넘은 page로 조회해도 404가 아니라 200과 빈 목록을 응답한다")
        void returnsEmpty_whenPageIsBeyondLast() {
            // given
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            productJpaRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            int beyondLastPage = 5;

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page={page}", HttpMethod.GET, HttpEntity.EMPTY, RESPONSE_TYPE, beyondLastPage);

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty()
            );
        }

        @Test
        @DisplayName("X-MEMBER-ID로 조회하면 그 회원이 좋아요한 상품의 isLiked가 true다")
        void passesMemberIdHeader_toIsLiked() {
            // given
            Long memberId = 1L;
            Brand savedBrand = brandJpaRepository.save(BrandFixture.aBrand());
            Product likedProduct = productJpaRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            productLikeJpaRepository.save(ProductLike.like(memberId, likedProduct.getId()));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-MEMBER-ID", String.valueOf(memberId));

            // when
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), RESPONSE_TYPE);

            // then
            ProductV1Dto.ProductSummaryResponse summary = response.getBody().data().content().get(0);
            assertThat(summary.isLiked()).isTrue();
        }

        private Product aProduct(Long brandId, Long price, ZonedDateTime openedAt) {
            return Product.create(ProductFixture.DEFAULT_NAME, ProductFixture.DEFAULT_DESCRIPTION,
                    ProductFixture.DEFAULT_IMAGE, brandId, Money.of(price), ProductFixture.DEFAULT_STOCK, openedAt);
        }
    }
}
