package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.fixture.MemberFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Disabled("5단계에서 활성화 — OrderUseCase가 아직 스켈레톤이다")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";
    private static final String HEADER_OF_IDEMPOTENCY_KEY = "Idempotency-Key";

    private Member member;
    private Brand brand;

    private final TestRestTemplate testRestTemplate;
    private final MemberRepository memberRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderV1ApiE2ETest(TestRestTemplate testRestTemplate, MemberRepository memberRepository,
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

    private HttpHeaders headersOf(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_OF_MEMBER_ID, member.getId().toString());
        headers.set(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey);
        return headers;
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class PlaceOrder {

        @Test
        @DisplayName("판매중인 상품을 주문하면 200과 결제 대기 상태의 주문번호를 반환한다")
        void returnsAwaitingPaymentOrder_whenProductIsOrderable() {
            // given
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            int orderQuantity = 2;
            Long expectedTotalAmount = ProductFixture.DEFAULT_PRICE.getAmount() * orderQuantity;

            HttpHeaders headers = headersOf(UUID.randomUUID().toString());
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), orderQuantity)));
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.PlaceOrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result())
                            .isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().orderNumber()).matches("^\\d{8}-[0-9A-Z]{8}$"),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(expectedTotalAmount),
                    () -> assertThat(response.getBody().data().orderedAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().isDuplicatedRequest()).isFalse(),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow()
                            .getStockQuantity().getValue()).isEqualTo(initialStock - orderQuantity)
            );
        }

        @Test
        @DisplayName("같은 Idempotency-Key로 다시 요청하면, 200응답이고 같은 주문번호를 반환하지만 isDuplicated는 참이고 재고는 한 번만 줄어든다")
        void returnsSameOrder_whenIdempotencyKeyIsReused() {
            // given
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            int orderQuantity = 2;

            String sameIdempotencyKey = UUID.randomUUID().toString();
            HttpHeaders sameKeyHeaders = headersOf(sameIdempotencyKey);
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), orderQuantity)));
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.PlaceOrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> first = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, sameKeyHeaders), responseType);
            ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> second = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, sameKeyHeaders), responseType);

            // then
            assertAll(
                    () -> assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(second.getBody().data().orderNumber())
                            .isEqualTo(first.getBody().data().orderNumber()),
                    () -> assertThat(first.getBody().data().isDuplicatedRequest()).isFalse(),
                    () -> assertThat(second.getBody().data().isDuplicatedRequest()).isTrue(),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow()
                            .getStockQuantity().getValue()).isEqualTo(initialStock - orderQuantity)
            );
        }

        @Test
        @DisplayName("판매중이 아닌 상품을 주문하면 404 Not Found를 반환한다")
        void returnsNotFound_whenProductIsNotOnSale() {
            // given
            Product offSaleProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStatus(brand.getId(), ProductStatus.OFF_SALE));
            int orderQuantity = 1;

            HttpHeaders headers = headersOf(UUID.randomUUID().toString());
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(offSaleProduct.getId(), orderQuantity)));
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.PlaceOrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("비활성 브랜드의 상품을 주문하면 404 Not Found를 반환한다")
        void returnsNotFound_whenBrandIsInactive() {
            // given
            Brand inactiveBrand = brandRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(inactiveBrand.getId()));
            int orderQuantity = 1;

            HttpHeaders headers = headersOf(UUID.randomUUID().toString());
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), orderQuantity)));
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.PlaceOrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("재고보다 많은 수량을 주문하면 409 Conflict를 반환한다")
        void returnsConflict_whenStockIsNotEnough() {
            // given
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
            int tooManyQuantity = ProductFixture.DEFAULT_STOCK.getValue() + 1;

            HttpHeaders headers = headersOf(UUID.randomUUID().toString());
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), tooManyQuantity)));
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.PlaceOrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenIdempotencyKeyHeaderIsMissing() {
            // given
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
            int orderQuantity = 1;

            HttpHeaders headersWithoutKey = new HttpHeaders();
            headersWithoutKey.set(HEADER_OF_MEMBER_ID, member.getId().toString());
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), orderQuantity)));
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.PlaceOrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.PlaceOrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headersWithoutKey), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
