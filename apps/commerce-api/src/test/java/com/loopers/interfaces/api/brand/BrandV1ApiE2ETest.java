package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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
class BrandV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(TestRestTemplate testRestTemplate, BrandJpaRepository brandJpaRepository, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("GET /api/v1/brands/{brandId}")
    class GetBrand {
        private static final String ENDPOINT = "/api/v1/brands/{brandId}";

        @Test
        @DisplayName("ACTIVE 브랜드를 조회하면 200과 브랜드 정보를 반환한다")
        void returnsBrandInfo_whenBrandIsActive() {
            Brand saved = brandJpaRepository.save(BrandFixture.aBrand());

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.GetBrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandV1Dto.GetBrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, saved.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(saved.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(BrandFixture.DEFAULT_NAME),
                    () -> assertThat(response.getBody().data().description()).isEqualTo(BrandFixture.DEFAULT_DESCRIPTION)
            );
        }

        @Test
        @DisplayName("존재하지 않는 브랜드를 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenBrandDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.GetBrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandV1Dto.GetBrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, Long.MAX_VALUE);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @Test
        @DisplayName("INACTIVE 브랜드를 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenBrandIsInactive() {
            Brand saved = brandJpaRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.GetBrandResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<BrandV1Dto.GetBrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, responseType, saved.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}
