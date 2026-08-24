package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, BrandJpaRepository brandJpaRepository,
                               ProductJpaRepository productJpaRepository, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
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
}
