package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.application.order.OrderUseCase;
import com.loopers.application.order.OrderUseCaseDto;
import com.loopers.application.payment.PaymentUseCaseDto;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderFixture;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.point.Point;
import com.loopers.domain.point.PointRepository;
import com.loopers.domain.point.PointServiceDto;
import com.loopers.domain.shared.Money;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
    private final OrderRepository orderRepository;
    private final PointRepository pointRepository;
    private final OrderUseCase orderUseCase;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderV1ApiE2ETest(TestRestTemplate testRestTemplate, MemberRepository memberRepository,
                             BrandRepository brandRepository, ProductRepository productRepository,
                             OrderRepository orderRepository, PointRepository pointRepository,
                             OrderUseCase orderUseCase, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.memberRepository = memberRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.pointRepository = pointRepository;
        this.orderUseCase = orderUseCase;
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
                    () -> assertThat(response.getBody().data().status()).isEqualTo(OrderUseCaseDto.OrderStatus.AWAITING_PAYMENT),
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

    @Nested
    @DisplayName("GET /api/v1/orders/{orderNumber}")
    class GetOrder {

        private static final String PAYMENT_ENDPOINT = "/api/v1/payments";
        private static final int ORDER_QUANTITY = 2;
        private static final Long ORDER_AMOUNT = ProductFixture.DEFAULT_PRICE.getAmount() * ORDER_QUANTITY;

        private Product product;

        @BeforeEach
        void setUpProduct() {
            this.product = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
        }

        private HttpHeaders memberHeadersOf(Long memberId) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_OF_MEMBER_ID, memberId.toString());
            return headers;
        }

        private String anAwaitingPaymentOrderNumber() {
            return orderUseCase.place(new OrderUseCaseDto.PlaceOrderInfo(
                    member.getId(),
                    UUID.randomUUID().toString(),
                    List.of(new OrderUseCaseDto.OrderItemInfo(product.getId(), ORDER_QUANTITY)))
            ).orderNumber();
        }

        private void aPointOf(Long balance) {
            Point point = Point.createInitial(new PointServiceDto.CreateInitialCommand(member.getId()));
            point.charge(Money.of(balance));
            pointRepository.save(point);
        }

        private void payWithPoint(String orderNumber) {
            PaymentV1Dto.PayRequest request = new PaymentV1Dto.PayRequest(
                    orderNumber, PaymentUseCaseDto.PaymentMethod.POINT, null, null);
            testRestTemplate.exchange(PAYMENT_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, memberHeadersOf(member.getId())),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {});
        }

        private ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> getOrder(Long memberId, String orderNumber) {
            return testRestTemplate.exchange(ENDPOINT + "/" + orderNumber, HttpMethod.GET,
                    new HttpEntity<>(memberHeadersOf(memberId)),
                    new ParameterizedTypeReference<>() {});
        }

        @Test
        @DisplayName("결제 대기 주문을 조회하면 200과 주문 라인을 반환하고 결제 정보는 비어 있다")
        void returnsOrderWithoutPayment_whenOrderIsAwaitingPayment() {
            // given
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> response = getOrder(member.getId(), orderNumber);

            // then
            OrderV1Dto.GetOrderResponse data = response.getBody().data();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(data.orderNumber()).isEqualTo(orderNumber),
                    () -> assertThat(data.status()).isEqualTo(OrderUseCaseDto.OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(data.totalAmount()).isEqualTo(ORDER_AMOUNT),
                    () -> assertThat(data.orderedAt()).isNotNull(),
                    () -> assertThat(data.paidAt()).isNull(),
                    () -> assertThat(data.lines()).hasSize(1),
                    () -> assertThat(data.lines().get(0).productId()).isEqualTo(product.getId()),
                    () -> assertThat(data.lines().get(0).productName()).isEqualTo(product.getName()),
                    () -> assertThat(data.lines().get(0).unitPrice()).isEqualTo(ProductFixture.DEFAULT_PRICE.getAmount()),
                    () -> assertThat(data.lines().get(0).quantity()).isEqualTo(ORDER_QUANTITY),
                    () -> assertThat(data.lines().get(0).lineAmount()).isEqualTo(ORDER_AMOUNT),
                    () -> assertThat(data.payment()).isNull()
            );
        }

        @Test
        @DisplayName("포인트로 결제한 주문을 조회하면 결제 완료 상태와 승인된 결제 정보를 반환한다")
        void returnsPaidOrderWithApprovedPayment_whenPaidWithPoint() {
            // given
            String orderNumber = anAwaitingPaymentOrderNumber();
            aPointOf(ORDER_AMOUNT);
            payWithPoint(orderNumber);

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> response = getOrder(member.getId(), orderNumber);

            // then
            OrderV1Dto.GetOrderResponse data = response.getBody().data();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(data.status()).isEqualTo(OrderUseCaseDto.OrderStatus.PAID),
                    () -> assertThat(data.paidAt()).isNotNull(),
                    () -> assertThat(data.payment().method()).isEqualTo(OrderUseCaseDto.PaymentMethod.POINT),
                    () -> assertThat(data.payment().status()).isEqualTo(OrderUseCaseDto.PaymentStatus.APPROVED),
                    () -> assertThat(data.payment().approvedAt()).isNotNull(),
                    () -> assertThat(data.payment().failureReason()).isNull()
            );
        }

        @Test
        @DisplayName("결제에 실패한 주문을 조회하면 결제 실패 상태와 실패 사유를 반환한다")
        void returnsPaymentFailedOrderWithReason_whenPaymentFailed() {
            // given
            String orderNumber = anAwaitingPaymentOrderNumber();
            aPointOf(ORDER_AMOUNT - 1);
            payWithPoint(orderNumber);

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> response = getOrder(member.getId(), orderNumber);

            // then
            OrderV1Dto.GetOrderResponse data = response.getBody().data();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(data.status()).isEqualTo(OrderUseCaseDto.OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(data.paidAt()).isNull(),
                    () -> assertThat(data.payment().status()).isEqualTo(OrderUseCaseDto.PaymentStatus.FAILED),
                    () -> assertThat(data.payment().approvedAt()).isNull(),
                    () -> assertThat(data.payment().failureReason()).isNotBlank()
            );
        }

        @Test
        @DisplayName("다른 회원의 주문을 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenOrderBelongsToAnotherMember() {
            // given
            String orderNumber = anAwaitingPaymentOrderNumber();
            Member otherMember = memberRepository.save(
                    MemberFixture.aMemberWithLoginIdAndEmail("otherUser", "other@test.com"));

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> response = getOrder(otherMember.getId(), orderNumber);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("접수만 되고 확정되지 않은 주문을 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenOrderIsPending() {
            // given
            Order pending = orderRepository.save(OrderFixture.aDraftedOrderOf(member.getId()));

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> response =
                    getOrder(member.getId(), pending.getOrderNumber().getValue());

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("확정에 실패한 주문을 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenOrderFailed() {
            // given
            Order failed = OrderFixture.aDraftedOrderOf(member.getId());
            failed.markOrderFailed();
            orderRepository.save(failed);

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> response =
                    getOrder(member.getId(), failed.getOrderNumber().getValue());

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 주문번호로 조회하면 404 Not Found를 반환한다")
        void returnsNotFound_whenOrderDoesNotExist() {
            // given
            String unknownOrderNumber = OrderFixture.DEFAULT_ORDER_NUMBER.getValue();

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.GetOrderResponse>> response = getOrder(member.getId(), unknownOrderNumber);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
