package com.loopers.interfaces.api.productlike;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.fixture.MemberFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductLikeV1ApiE2ETest {

    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";

    private Member member;
    private Brand brand;

    private final TestRestTemplate testRestTemplate;
    private final MemberRepository memberRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductLikeV1ApiE2ETest(TestRestTemplate testRestTemplate, MemberRepository memberRepository,
                                   BrandRepository brandRepository, ProductRepository productRepository,
                                   DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.memberRepository = memberRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        this.member = memberRepository.save(MemberFixture.aMember());
        this.brand = brandRepository.save(BrandFixture.aBrand());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/like/products/{productId}")
    class Like {
        private static final String ENDPOINT = "/api/v1/like/products/{productId}";

        @Test
        @DisplayName("ON_SALE 상품에 처음 좋아요하면 200과 중복 아님 응답을 반환한다")
        void returnsNotDuplicated_whenFirstLike() {
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());

            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductLikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(headers), responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().isDuplicatedRequest()).isFalse()
            );
        }

        @Test
        @DisplayName("같은 요청을 다시 보내면 200과 중복 응답을 반환한다")
        void returnsDuplicated_whenLikedAgain() {
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());

            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(headers), responseType, savedProduct.getId());

            ResponseEntity<ApiResponse<ProductLikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(headers), responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().isDuplicatedRequest()).isTrue(),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow().getLikeCount())
                            .isEqualTo(1)
            );
        }

        @Test
        @DisplayName("OFF_SALE 상품에 좋아요하면 404 Not Found를 반환한다")
        void returnsNotFound_whenProductIsOffSale() {
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStatus(brand.getId(), ProductStatus.OFF_SALE));

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());

            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductLikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(headers), responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요하면 404 Not Found를 반환한다")
        void returnsNotFound_whenProductDoesNotExist() {
            Long nonExistentProductId = Long.MAX_VALUE;

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());

            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.LikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductLikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(headers), responseType, nonExistentProductId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/like/products/{productId}")
    class Unlike {
        private static final String ENDPOINT = "/api/v1/like/products/{productId}";

        @Test
        @DisplayName("좋아요한 상품을 취소하면 200과 중복 아님 응답을 반환한다")
        void returnsNotDuplicated_whenLiked() {
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());

            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.UnlikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(headers),
                    responseType, savedProduct.getId());

            ResponseEntity<ApiResponse<ProductLikeV1Dto.UnlikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.DELETE, new HttpEntity<>(headers), responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().isDuplicatedRequest()).isFalse(),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow().getLikeCount())
                            .isEqualTo(0)
            );
        }

        @Test
        @DisplayName("좋아요하지 않은 상품을 취소하면 200과 중복 응답을 반환한다")
        void returnsDuplicated_whenNotLiked() {
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());

            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.UnlikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductLikeV1Dto.UnlikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.DELETE, new HttpEntity<>(headers), responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().isDuplicatedRequest()).isTrue()
            );
        }

        @Test
        @DisplayName("OFF_SALE 상품도 좋아요를 취소할 수 있다")
        void unlikes_whenProductIsOffSale() {
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());

            ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.UnlikeResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(headers),
                    responseType, savedProduct.getId());
            savedProduct.putOffSale();
            productRepository.save(savedProduct);

            ResponseEntity<ApiResponse<ProductLikeV1Dto.UnlikeResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.DELETE, new HttpEntity<>(headers), responseType, savedProduct.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().isDuplicatedRequest()).isFalse()
            );
        }
    }
}
