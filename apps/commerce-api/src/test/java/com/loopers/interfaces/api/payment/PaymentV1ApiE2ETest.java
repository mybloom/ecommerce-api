package com.loopers.interfaces.api.payment;

import com.loopers.application.order.OrderUseCase;
import com.loopers.application.order.OrderUseCaseDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderNumber;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.point.Point;
import com.loopers.domain.point.PointRepository;
import com.loopers.domain.point.PointServiceDto;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.shared.Money;
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
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";
    private static final int ORDER_QUANTITY = 2;
    private static final Long ORDER_AMOUNT = ProductFixture.DEFAULT_PRICE.getAmount() * ORDER_QUANTITY;

    private Member member;
    private Brand brand;
    private Product product;

    private final TestRestTemplate testRestTemplate;
    private final OrderUseCase orderUseCase;
    private final MemberRepository memberRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PointRepository pointRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public PaymentV1ApiE2ETest(TestRestTemplate testRestTemplate, OrderUseCase orderUseCase,
                               MemberRepository memberRepository, BrandRepository brandRepository,
                               ProductRepository productRepository, OrderRepository orderRepository,
                               PointRepository pointRepository, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.orderUseCase = orderUseCase;
        this.memberRepository = memberRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.pointRepository = pointRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        this.member = memberRepository.save(MemberFixture.aMember());
        this.brand = brandRepository.save(BrandFixture.aBrand());
        this.product = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders headersOf(Long memberId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_OF_MEMBER_ID, memberId.toString());
        return headers;
    }

    /**
     * 실제 주문 흐름을 그대로 태워 재고가 빠진 결제대기 주문을 만든다.
     * 재고 복원을 검증하려면 재고가 실제로 차감되어 있어야 한다.
     */
    private String anAwaitingPaymentOrderNumber(Long memberId) {
        OrderUseCaseDto.PlaceOrderResult placed = orderUseCase.place(new OrderUseCaseDto.PlaceOrderInfo(
                memberId,
                UUID.randomUUID().toString(),
                List.of(new OrderUseCaseDto.OrderItemInfo(product.getId(), ORDER_QUANTITY))));

        return placed.orderNumber();
    }

    private Point aChargedPoint(Long memberId, Long balance) {
        Point point = Point.createInitial(new PointServiceDto.CreateInitialCommand(memberId));
        point.charge(Money.of(balance));

        return pointRepository.save(point);
    }

    private ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> requestPayment(Long memberId, String orderNumber) {
        PaymentV1Dto.PayRequest request = new PaymentV1Dto.PayRequest(orderNumber, PaymentMethod.POINT);
        ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PayResponse>> responseType =
                new ParameterizedTypeReference<>() {};

        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headersOf(memberId)), responseType);
    }

    private Order findOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(OrderNumber.of(orderNumber)).orElseThrow();
    }

    private int findStockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity().getValue();
    }

    private Long findBalanceOf(Long memberId) {
        return pointRepository.findByMemberId(memberId).orElseThrow().getBalance().getAmount();
    }

    @Nested
    @DisplayName("POST /api/v1/payments")
    class Pay {

        @Test
        @DisplayName("잔액이 충분하면 200과 승인된 결제를 반환하고, 주문은 결제완료가 되며 주문 금액만큼 포인트가 차감된다")
        void returnsApprovedPayment_whenBalanceIsEnough() {
            // given
            Long initialBalance = ORDER_AMOUNT + 50_000L;
            aChargedPoint(member.getId(), initialBalance);
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestPayment(member.getId(), orderNumber);

            // then
            Order paidOrder = findOrder(orderNumber);
            Long remainingBalance = findBalanceOf(member.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orderNumber()).isEqualTo(orderNumber),
                    () -> assertThat(response.getBody().data().method()).isEqualTo(PaymentMethod.POINT),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.APPROVED),
                    () -> assertThat(response.getBody().data().amount()).isEqualTo(ORDER_AMOUNT),
                    () -> assertThat(response.getBody().data().approvedAt()).isNotNull(),
                    () -> assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID),
                    () -> assertThat(paidOrder.getPaidAt()).isNotNull(),
                    () -> assertThat(remainingBalance).isEqualTo(initialBalance - ORDER_AMOUNT)
            );
        }

        @Test
        @DisplayName("잔액이 부족하면 409를 반환하고, 주문은 결제실패가 되며 확보했던 재고가 복원되고 포인트는 그대로다")
        void restoresStock_whenBalanceIsNotEnough() {
            // given
            Long insufficientBalance = ORDER_AMOUNT - 1L;
            aChargedPoint(member.getId(), insufficientBalance);
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestPayment(member.getId(), orderNumber);

            // then
            Order failedOrder = findOrder(orderNumber);
            int restoredStock = findStockOf(product);
            Long remainingBalance = findBalanceOf(member.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(failedOrder.getPaidAt()).isNull(),
                    () -> assertThat(restoredStock).isEqualTo(initialStock),
                    () -> assertThat(remainingBalance).isEqualTo(insufficientBalance)
            );
        }

        @Test
        @DisplayName("다른 회원의 주문을 결제하면 404를 반환하고 그 주문은 결제대기로 남는다")
        void returnsNotFound_whenOrderBelongsToAnotherMember() {
            // given
            Member otherMember = memberRepository.save(MemberFixture.aMemberWithLoginId("otherUser"));
            aChargedPoint(otherMember.getId(), ORDER_AMOUNT);
            String orderNumberOfOwner = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestPayment(otherMember.getId(), orderNumberOfOwner);

            // then
            Order untouchedOrder = findOrder(orderNumberOfOwner);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(untouchedOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT)
            );
        }

        @Test
        @DisplayName("이미 결제된 주문을 다시 결제하면 409를 반환하고 포인트가 두 번 차감되지 않는다")
        void returnsConflict_whenOrderIsAlreadyPaid() {
            // given
            Long initialBalance = ORDER_AMOUNT * 2;
            aChargedPoint(member.getId(), initialBalance);
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());
            requestPayment(member.getId(), orderNumber);

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestPayment(member.getId(), orderNumber);

            // then
            Long remainingBalance = findBalanceOf(member.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(remainingBalance).isEqualTo(initialBalance - ORDER_AMOUNT)
            );
        }

        @Test
        @DisplayName("존재하지 않는 주문번호로 결제하면 404를 반환한다")
        void returnsNotFound_whenOrderNumberDoesNotExist() {
            // given
            aChargedPoint(member.getId(), ORDER_AMOUNT);
            String unknownOrderNumber = "20260827-ZZZZZZZZ";

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestPayment(member.getId(), unknownOrderNumber);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
