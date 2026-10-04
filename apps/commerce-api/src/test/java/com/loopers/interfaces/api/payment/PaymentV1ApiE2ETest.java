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
import com.loopers.application.payment.PaymentUseCaseDto;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgConnectionFailedException;
import com.loopers.domain.payment.PgHostUnresolvedException;
import com.loopers.domain.payment.PgNotProcessedException;
import com.loopers.domain.payment.PgRateLimitedException;
import com.loopers.domain.payment.PgRejectedException;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.payment.PgUnavailableException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String CALLBACK_ENDPOINT = "/api/v1/payments/pg/callback";
    private static final String DEFAULT_CARD_NO = "1234-5678-9814-1451";
    private static final String DEFAULT_TRANSACTION_KEY = "20260828:TR:0a8ec1";
    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";
    private static final int ORDER_QUANTITY = 2;
    private static final Long ORDER_AMOUNT = ProductFixture.DEFAULT_PRICE.getAmount() * ORDER_QUANTITY;

    static Stream<Arguments> notProcessedFailures() {
        return Stream.of(
                Arguments.of(new PgConnectionFailedException("PG에 연결하지 못했습니다."), "PG_CONNECTION_FAILED"),
                Arguments.of(new PgHostUnresolvedException("PG 주소를 찾지 못했습니다."), "PG_CONNECTION_FAILED"),
                Arguments.of(new PgRateLimitedException("PG가 요청 한도를 넘었다고 알렸습니다.", Duration.ofSeconds(1)), "PG_RATE_LIMITED"),
                Arguments.of(new PgUnavailableException("PG가 지금은 요청을 받을 수 없다고 알렸습니다.", null), "PG_UNAVAILABLE")
        );
    }

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
    private final PaymentRepository paymentRepository;
    private final DatabaseCleanUp databaseCleanUp;

    /**
     * 실제 PG는 40% 확률로 거절하고 승인/실패도 랜덤이라 원하는 경로를 만들 수 없다.
     * 요청 형식이 맞는지는 @Tag("external") 테스트가 진짜 PG로 따로 본다
     * (참고: 03_외부시스템_테스트전략.md).
     */
    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    public PaymentV1ApiE2ETest(TestRestTemplate testRestTemplate, OrderUseCase orderUseCase,
                               MemberRepository memberRepository, BrandRepository brandRepository,
                               ProductRepository productRepository, OrderRepository orderRepository,
                               PointRepository pointRepository, PaymentRepository paymentRepository,
                               DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.orderUseCase = orderUseCase;
        this.memberRepository = memberRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.pointRepository = pointRepository;
        this.paymentRepository = paymentRepository;
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
        PaymentV1Dto.PayRequest request = new PaymentV1Dto.PayRequest(orderNumber, PaymentUseCaseDto.PaymentMethod.POINT, null, null);
        ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PayResponse>> responseType =
                new ParameterizedTypeReference<>() {};

        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headersOf(memberId)), responseType);
    }

    private ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> requestCardPayment(Long memberId, String orderNumber) {
        PaymentV1Dto.PayRequest request = new PaymentV1Dto.PayRequest(
                orderNumber, PaymentUseCaseDto.PaymentMethod.CARD, PaymentUseCaseDto.CardType.SAMSUNG, DEFAULT_CARD_NO);
        ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PayResponse>> responseType =
                new ParameterizedTypeReference<>() {};

        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headersOf(memberId)), responseType);
    }

    /**
     * PG를 흉내내 콜백을 직접 쏜다. 실제 PG는 1~5초 뒤 별도 스레드로 보내지만,
     * 여기서는 도착 시점과 순서를 확정해야 단언할 수 있다.
     */
    private ResponseEntity<ApiResponse<Object>> sendCallback(
            String orderNumber, PaymentUseCaseDto.PgTransactionStatus status, Long amount, String reason) {
        PaymentV1Dto.PgCallbackRequest request = new PaymentV1Dto.PgCallbackRequest(
                DEFAULT_TRANSACTION_KEY, orderNumber, status, amount, reason);
        ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};

        return testRestTemplate.exchange(
                CALLBACK_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);
    }

    private Order findOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(OrderNumber.of(orderNumber)).orElseThrow();
    }

    private int findStockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity().getValue();
    }

    private Payment findPaymentOf(String orderNumber) {
        return paymentRepository.findByOrderId(findOrder(orderNumber).getId()).orElseThrow();
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
                    () -> assertThat(response.getBody().data().method()).isEqualTo(PaymentUseCaseDto.PaymentMethod.POINT),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentUseCaseDto.PaymentStatus.APPROVED),
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
            Member otherMember = memberRepository.save(
                    MemberFixture.aMemberWithLoginIdAndEmail("otherUser", "other@test.com"));
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

    @Nested
    @DisplayName("POST /api/v1/payments - 카드 결제")
    class CardPay {

        @Test
        @DisplayName("카드 결제를 요청하면 200과 승인대기 결제를 반환하고, 주문은 결제대기로 남으며 승인 시각은 비어 있다")
        void returnsPendingPayment_whenCardPaymentIsRequested() {
            // given
            when(paymentGateway.requestApproval(any())).thenReturn(
                    new PaymentGatewayDto.Approval(DEFAULT_TRANSACTION_KEY, PgTransactionStatus.PENDING));
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestCardPayment(member.getId(), orderNumber);

            // then
            Order awaitingOrder = findOrder(orderNumber);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().method()).isEqualTo(PaymentUseCaseDto.PaymentMethod.CARD),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentUseCaseDto.PaymentStatus.PENDING),
                    () -> assertThat(response.getBody().data().amount()).isEqualTo(ORDER_AMOUNT),
                    () -> assertThat(response.getBody().data().approvedAt()).isNull(),
                    () -> assertThat(awaitingOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT)
            );
        }

        @Test
        @DisplayName("PG가 승인 요청을 명시적으로 거절하면 502를 반환하고, 주문은 결제실패가 되며 확보했던 재고가 복원된다")
        void returnsBadGateway_whenPgRejects() {
            // given
            when(paymentGateway.requestApproval(any()))
                    .thenThrow(new PgRejectedException("PG 승인 요청이 거절되었습니다."));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestCardPayment(member.getId(), orderNumber);

            // then
            Order failedOrder = findOrder(orderNumber);
            int restoredStock = findStockOf(product);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(restoredStock).isEqualTo(initialStock)
            );
        }

        /**
         * 재시도가 끝난 뒤의 최종 결과다. HTTP 상태는 모두 502로 같고, 클라이언트는 에러 코드로
         * 안내를 고른다 (참고: 07_payment.md E.4 정한 것 4·5번).
         */
        @ParameterizedTest(name = "{0} → {1}")
        @MethodSource("com.loopers.interfaces.api.payment.PaymentV1ApiE2ETest#notProcessedFailures")
        @DisplayName("PG가 요청을 처리하지 않은 실패가 exception 이면 502와 errorCode 를 반환하고, 주문은 결제실패가 되며 확보했던 재고가 복원된다")
        void returnsBadGatewayWithErrorCode_whenPgDidNotProcess(PgNotProcessedException exception, String errorCode) {
            // given
            when(paymentGateway.requestApproval(any())).thenThrow(exception);
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestCardPayment(member.getId(), orderNumber);

            // then
            Order failedOrder = findOrder(orderNumber);
            int restoredStock = findStockOf(product);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(errorCode),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(restoredStock).isEqualTo(initialStock)
            );
        }

        @Test
        @DisplayName("PG 처리 여부를 알 수 없으면 200과 PENDING을 반환하고, 주문은 결제대기로 남으며 재고는 묶인 채로 남는다")
        void returnsPending_whenPgResultIsUnknown() {
            // given
            when(paymentGateway.requestApproval(any()))
                    .thenThrow(new PgResultUnknownException("PG가 처리 여부를 알 수 없는 응답을 주었습니다."));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response =
                    requestCardPayment(member.getId(), orderNumber);

            // then
            Order awaitingOrder = findOrder(orderNumber);
            int heldStock = findStockOf(product);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentUseCaseDto.PaymentStatus.PENDING),
                    () -> assertThat(awaitingOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(heldStock).isEqualTo(initialStock - ORDER_QUANTITY)
            );
        }

        @Test
        @DisplayName("카드 번호 형식이 어긋나면 400을 반환하고, 결제가 만들어지지 않아 주문은 결제대기로 남는다")
        void returnsBadRequest_whenCardNoFormatIsInvalid() {
            // given
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());
            String malformedCardNo = "1234-5678";

            PaymentV1Dto.PayRequest request = new PaymentV1Dto.PayRequest(
                    orderNumber, PaymentUseCaseDto.PaymentMethod.CARD, PaymentUseCaseDto.CardType.SAMSUNG, malformedCardNo);
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PayResponse>> responseType =
                    new ParameterizedTypeReference<>() {};

            // when
            ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headersOf(member.getId())), responseType);

            // then
            Order untouchedOrder = findOrder(orderNumber);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(untouchedOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT)
            );
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/pg/callback")
    class PgCallback {

        private String aPendingCardOrderNumber() {
            when(paymentGateway.requestApproval(any())).thenReturn(
                    new PaymentGatewayDto.Approval(DEFAULT_TRANSACTION_KEY, PgTransactionStatus.PENDING));
            String orderNumber = anAwaitingPaymentOrderNumber(member.getId());
            requestCardPayment(member.getId(), orderNumber);

            return orderNumber;
        }

        @Test
        @DisplayName("승인 콜백을 받으면 200이고, 결제는 승인완료가 되며 주문은 결제완료가 되고 재고는 그대로다")
        void approvesPayment_whenCallbackIsSuccess() {
            // given
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = aPendingCardOrderNumber();

            // when
            ResponseEntity<ApiResponse<Object>> response =
                    sendCallback(orderNumber, PaymentUseCaseDto.PgTransactionStatus.SUCCESS, ORDER_AMOUNT, null);

            // then
            Payment approvedPayment = findPaymentOf(orderNumber);
            Order paidOrder = findOrder(orderNumber);
            int remainingStock = findStockOf(product);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(approvedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED),
                    () -> assertThat(approvedPayment.getTransactionKey()).isEqualTo(DEFAULT_TRANSACTION_KEY),
                    () -> assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID),
                    () -> assertThat(paidOrder.getPaidAt()).isNotNull(),
                    () -> assertThat(remainingStock).isEqualTo(initialStock - ORDER_QUANTITY)
            );
        }

        @Test
        @DisplayName("실패 콜백을 받으면 200이고, PG가 보낸 사유가 남으며 재고가 복원되고 주문은 결제실패가 된다")
        void restoresStock_whenCallbackIsFailed() {
            // given
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = aPendingCardOrderNumber();
            String pgReason = "한도초과입니다. 다른 카드를 선택해주세요.";

            // when
            ResponseEntity<ApiResponse<Object>> response =
                    sendCallback(orderNumber, PaymentUseCaseDto.PgTransactionStatus.FAILED, ORDER_AMOUNT, pgReason);

            // then
            Payment failedPayment = findPaymentOf(orderNumber);
            Order failedOrder = findOrder(orderNumber);
            int restoredStock = findStockOf(product);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(failedPayment.getFailureReason()).isEqualTo(pgReason),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(restoredStock).isEqualTo(initialStock)
            );
        }

        @Test
        @DisplayName("같은 실패 콜백이 두 번 와도 200이고, 재고가 두 번 복원되지 않는다")
        void restoresStockOnce_whenSameFailureCallbackArrivesTwice() {
            // given
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = aPendingCardOrderNumber();
            String pgReason = "한도초과입니다. 다른 카드를 선택해주세요.";
            sendCallback(orderNumber, PaymentUseCaseDto.PgTransactionStatus.FAILED, ORDER_AMOUNT, pgReason);

            // when
            ResponseEntity<ApiResponse<Object>> response =
                    sendCallback(orderNumber, PaymentUseCaseDto.PgTransactionStatus.FAILED, ORDER_AMOUNT, pgReason);

            // then
            int restoredStock = findStockOf(product);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(restoredStock).isEqualTo(initialStock)
            );
        }

        @Test
        @DisplayName("승인 금액이 주문 총액과 다르면 성공 콜백이어도 결제가 실패하고 주문도 결제실패가 되며 재고가 복원된다")
        void failsPayment_whenApprovedAmountDiffers() {
            // given
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = aPendingCardOrderNumber();
            Long tamperedAmount = ORDER_AMOUNT - 1L;

            // when
            ResponseEntity<ApiResponse<Object>> response =
                    sendCallback(orderNumber, PaymentUseCaseDto.PgTransactionStatus.SUCCESS, tamperedAmount, null);

            // then
            Payment failedPayment = findPaymentOf(orderNumber);
            Order failedOrder = findOrder(orderNumber);
            int restoredStock = findStockOf(product);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(restoredStock).isEqualTo(initialStock)
            );
        }

        /**
         * 처리에 실패하면 5xx로 응답해 PG의 재전송을 유도한다. 200을 주면 재전송이 오지 않아
         * 결제가 PENDING에 영구히 남는다 (참고: Payment-005).
         */
        @Test
        @DisplayName("결제가 없는 주문번호로 콜백이 오면 404를 반환한다")
        void returnsNotFound_whenNoPaymentExistsForOrder() {
            // given
            String orderNumberWithoutPayment = anAwaitingPaymentOrderNumber(member.getId());

            // when
            ResponseEntity<ApiResponse<Object>> response =
                    sendCallback(orderNumberWithoutPayment, PaymentUseCaseDto.PgTransactionStatus.SUCCESS, ORDER_AMOUNT, null);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
