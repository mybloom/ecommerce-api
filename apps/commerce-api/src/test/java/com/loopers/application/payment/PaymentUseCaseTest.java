package com.loopers.application.payment;

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
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayDto;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgRejectedException;
import com.loopers.domain.payment.PgResultUnknownException;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.point.Point;
import com.loopers.domain.point.PointRepository;
import com.loopers.domain.point.PointServiceDto;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fixture.MemberFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * E2E(PaymentV1ApiE2ETest)가 이미 보는 것 — HTTP 상태, 주문 상태, 포인트 잔액, 재고 복원 — 은 여기서 되풀이하지 않는다.
 * 이 테스트는 <b>E2E가 볼 수 없는 것</b>만 담는다: 결제 애그리거트의 최종 상태와 동시성.
 */
@SpringBootTest
class PaymentUseCaseTest {

    private static final int ORDER_QUANTITY = 2;
    private static final String DEFAULT_CARD_NO = "1234-5678-9814-1451";
    private static final String DEFAULT_TRANSACTION_KEY = "20260828:TR:0a8ec1";
    private static final Long ORDER_AMOUNT = ProductFixture.DEFAULT_PRICE.getAmount() * ORDER_QUANTITY;

    private Member member;
    private Brand brand;
    private Product product;

    private final PaymentUseCase paymentUseCase;
    private final PaymentRepository paymentRepository;
    private final OrderUseCase orderUseCase;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PointRepository pointRepository;
    private final DatabaseCleanUp databaseCleanUp;

    /**
     * PG는 우리 프로세스 밖이라 승인/거절을 의도적으로 만들 수 없다. 이 테스트가 보려는 것은
     * <b>PG가 어떻게 답하든 우리가 무엇을 남기는가</b>라, 답을 고정한다 (참고: 03_외부시스템_테스트전략.md).
     */
    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    public PaymentUseCaseTest(PaymentUseCase paymentUseCase, PaymentRepository paymentRepository,
                              OrderUseCase orderUseCase, OrderRepository orderRepository,
                              MemberRepository memberRepository, BrandRepository brandRepository,
                              ProductRepository productRepository, PointRepository pointRepository,
                              DatabaseCleanUp databaseCleanUp) {
        this.paymentUseCase = paymentUseCase;
        this.paymentRepository = paymentRepository;
        this.orderUseCase = orderUseCase;
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
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

    /**
     * 실제 주문 흐름을 그대로 태워 재고가 빠진 결제대기 주문을 만든다.
     */
    private String anAwaitingPaymentOrderNumber() {
        OrderUseCaseDto.PlaceOrderResult placed = orderUseCase.place(new OrderUseCaseDto.PlaceOrderInfo(
                member.getId(),
                UUID.randomUUID().toString(),
                List.of(new OrderUseCaseDto.OrderItemInfo(product.getId(), ORDER_QUANTITY))));

        return placed.orderNumber();
    }

    private PaymentUseCaseDto.PayInfo aPayInfo(String orderNumber) {
        return new PaymentUseCaseDto.PayInfo(member.getId(), orderNumber, PaymentUseCaseDto.PaymentMethod.POINT, null, null);
    }

    private void aChargedPoint(Long balance) {
        Point point = Point.createInitial(new PointServiceDto.CreateInitialCommand(member.getId()));
        point.charge(Money.of(balance));

        pointRepository.save(point);
    }

    private Payment findPaymentOf(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(OrderNumber.of(orderNumber)).orElseThrow();

        return paymentRepository.findByOrderId(order.getId()).orElseThrow();
    }

    /**
     * 주문 확정 뒤 상품이 내려간 상황. 재고 복원이 retrieveForUpdate에서 막힌다.
     */
    private void aHiddenProduct() {
        Product hidden = productRepository.findById(product.getId()).orElseThrow();
        hidden.hide();

        productRepository.save(hidden);
    }

    private int findStock() {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity().getValue();
    }

    private PaymentUseCaseDto.PayInfo aCardPayInfo(String orderNumber) {
        return new PaymentUseCaseDto.PayInfo(
                member.getId(), orderNumber, PaymentUseCaseDto.PaymentMethod.CARD, PaymentUseCaseDto.CardType.SAMSUNG, DEFAULT_CARD_NO);
    }

    private Order findOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(OrderNumber.of(orderNumber)).orElseThrow();
    }

    private int findStockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity().getValue();
    }

    private Long findBalance() {
        return pointRepository.findByMemberId(member.getId()).orElseThrow().getBalance().getAmount();
    }

    @Nested
    @DisplayName("pay - 결제가 남기는 기록")
    class PaymentRecord {

        @Test
        @DisplayName("승인되면 결제가 APPROVED로 남고 주문 금액과 승인 시각이 기록되며 PG 거래 식별자는 없다")
        void recordsApprovedPayment() {
            // given
            aChargedPoint(ORDER_AMOUNT);
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            paymentUseCase.pay(aPayInfo(orderNumber));

            // then
            Payment approvedPayment = findPaymentOf(orderNumber);

            assertAll(
                    () -> assertThat(approvedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED),
                    () -> assertThat(approvedPayment.getMethod()).isEqualTo(PaymentMethod.POINT),
                    () -> assertThat(approvedPayment.getAmount()).isEqualTo(Money.of(ORDER_AMOUNT)),
                    () -> assertThat(approvedPayment.getApprovedAt()).isNotNull(),
                    () -> assertThat(approvedPayment.getTransactionKey()).isNull(),
                    () -> assertThat(approvedPayment.getFailureReason()).isNull()
            );
        }

        @Test
        @DisplayName("잔액이 부족해 실패하면 결제가 FAILED로 남고 실패 사유가 기록되며 승인 시각은 없다")
        void recordsFailedPayment() {
            // given
            Long insufficientBalance = ORDER_AMOUNT - 1L;
            aChargedPoint(insufficientBalance);
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            Throwable thrown = catchThrowable(() -> paymentUseCase.pay(aPayInfo(orderNumber)));

            // then
            Payment failedPayment = findPaymentOf(orderNumber);

            assertAll(
                    () -> assertThat(thrown).isNotNull(),
                    () -> assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(failedPayment.getFailureReason()).isNotBlank(),
                    () -> assertThat(failedPayment.getApprovedAt()).isNull()
            );
        }
    }

    @Nested
    @DisplayName("pay - 결제 실패 보상")
    class Compensation {

        @Test
        @DisplayName("결제가 실패하면 그 사이 상품이 내려갔어도 재고가 복원되고 결제는 FAILED로 남는다")
        void restoresStock_whenProductBecameHidden() {
            // given
            Long insufficientBalance = ORDER_AMOUNT - 1L;
            aChargedPoint(insufficientBalance);
            String orderNumber = anAwaitingPaymentOrderNumber();

            aHiddenProduct();

            // when
            Throwable thrown = catchThrowable(() -> paymentUseCase.pay(aPayInfo(orderNumber)));

            // then
            int expectedStock = ProductFixture.DEFAULT_STOCK.getValue();

            assertAll(
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.CONFLICT),
                    () -> assertThat(findStock()).isEqualTo(expectedStock),
                    () -> assertThat(findPaymentOf(orderNumber).getStatus()).isEqualTo(PaymentStatus.FAILED)
            );
        }

        /**
         * 보상이 실패하는 상황은 실제 스택으로 만들 수 없어(복원이 더 이상 상태를 보지 않는다)
         * 여기서만 목을 쓴다. 검증 대상이 DB가 아니라 예외를 어디에 매다는지이므로 컨텍스트도 필요 없다.
         */
        @Test
        @DisplayName("보상이 실패하면 결제 실패의 원래 원인이 전파되고 보상 실패는 suppressed로 함께 남는다")
        void propagatesOriginalCause_whenCompensationFails() {
            // given
            CoreException approveFailure = new CoreException(ErrorType.CONFLICT, "잔액이 부족합니다.");
            CoreException compensationFailure = new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");

            PaymentProcessor failingProcessor = mock(PaymentProcessor.class);
            when(failingProcessor.accept(any())).thenReturn(mock(Payment.class));
            when(failingProcessor.markFailed(any(), any(), any())).thenThrow(compensationFailure);

            PaymentStrategy failingStrategy = mock(PaymentStrategy.class);
            when(failingStrategy.method()).thenReturn(PaymentMethod.POINT);
            when(failingStrategy.approve(any(), any())).thenThrow(approveFailure);

            PaymentUseCase useCase = new PaymentUseCase(failingProcessor, new PaymentStrategies(List.of(failingStrategy)));

            // when
            Throwable thrown = catchThrowable(() -> useCase.pay(aPayInfo("20260827-A3F9K2QP")));

            // then
            assertAll(
                    () -> assertThat(thrown).isSameAs(approveFailure),
                    () -> assertThat(thrown.getSuppressed()).containsExactly(compensationFailure)
            );
        }
    }

    @Nested
    @DisplayName("pay - 동시 결제 요청")
    class ConcurrentPay {

        @Test
        @DisplayName("같은 주문에 동시에 결제를 요청하면 한 건만 승인되고 포인트도 한 번만 차감된다")
        void approvesOnlyOne_whenSameOrderIsPaidConcurrently() throws InterruptedException {
            // given
            Long initialBalance = ORDER_AMOUNT * 2;
            aChargedPoint(initialBalance);
            String orderNumber = anAwaitingPaymentOrderNumber();
            int concurrentRequestCount = 2;

            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentRequestCount);
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequestCount);

            // when
            for (int i = 0; i < concurrentRequestCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        paymentUseCase.pay(aPayInfo(orderNumber));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(20, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            Long remainingBalance = findBalance();

            assertAll(
                    () -> assertThat(failures).hasSize(1),
                    () -> assertThat(remainingBalance).isEqualTo(initialBalance - ORDER_AMOUNT)
            );
        }
    }

    @Nested
    @DisplayName("pay - 카드 결제")
    class CardPay {

        @Test
        @DisplayName("PG가 승인 요청을 접수하면 결제는 CARD/PENDING으로 남고 승인 시각도 거래 식별자도 없으며 주문은 결제대기에 머문다")
        void recordsPendingCardPayment() {
            // given
            when(paymentGateway.requestApproval(any())).thenReturn(
                    new PaymentGatewayDto.Approval(DEFAULT_TRANSACTION_KEY, PgTransactionStatus.PENDING));
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            PaymentUseCaseDto.PayResult result = paymentUseCase.pay(aCardPayInfo(orderNumber));

            // then
            Payment pendingPayment = findPaymentOf(orderNumber);
            Order awaitingOrder = findOrder(orderNumber);

            assertAll(
                    () -> assertThat(result.status()).isEqualTo(PaymentUseCaseDto.PaymentStatus.PENDING),
                    () -> assertThat(pendingPayment.getMethod()).isEqualTo(PaymentMethod.CARD),
                    () -> assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(pendingPayment.getAmount()).isEqualTo(Money.of(ORDER_AMOUNT)),
                    () -> assertThat(pendingPayment.getApprovedAt()).isNull(),
                    () -> assertThat(pendingPayment.getTransactionKey()).isNull(),
                    () -> assertThat(awaitingOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT)
            );
        }

        @Test
        @DisplayName("카드 결제는 포인트를 차감하지 않는다")
        void doesNotUsePoint_whenMethodIsCard() {
            // given
            when(paymentGateway.requestApproval(any())).thenReturn(
                    new PaymentGatewayDto.Approval(DEFAULT_TRANSACTION_KEY, PgTransactionStatus.PENDING));
            Long initialBalance = ORDER_AMOUNT + 50_000L;
            aChargedPoint(initialBalance);
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            paymentUseCase.pay(aCardPayInfo(orderNumber));

            // then
            Long remainingBalance = findBalance();

            assertThat(remainingBalance).isEqualTo(initialBalance);
        }

        @Test
        @DisplayName("PG가 거절하면 결제는 FAILED로 남고 재고가 복원되며 주문은 결제실패가 된다")
        void restoresStock_whenPgRejects() {
            // given
            when(paymentGateway.requestApproval(any()))
                    .thenThrow(new PgRejectedException("PG 승인 요청이 거절되었습니다."));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            Throwable thrown = catchThrowable(() -> paymentUseCase.pay(aCardPayInfo(orderNumber)));

            // then
            Payment failedPayment = findPaymentOf(orderNumber);
            Order failedOrder = findOrder(orderNumber);
            int restoredStock = findStockOf(product);

            assertAll(
                    () -> assertThat(thrown).isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_GATEWAY)),
                    () -> assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(failedPayment.getFailureReason()).isNotBlank(),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                    () -> assertThat(restoredStock).isEqualTo(initialStock)
            );
        }

        /**
         * PG가 처리했는지 모르는 상태에서 실패로 확정하면, PG가 승인한 결제를 되돌릴 수 없게 만든다.
         * <b>PgResultUnknownException이 CoreException이 아니라는 사실이 이 동작을 강제한다</b>
         * (참고: Payment-010).
         */
        @Test
        @DisplayName("PG 응답을 받지 못하면 보상이 걸리지 않아 결제는 PENDING, 주문은 결제대기, 재고는 묶인 채로 남는다")
        void keepsPending_whenPgResultIsUnknown() {
            // given
            when(paymentGateway.requestApproval(any()))
                    .thenThrow(new PgResultUnknownException("PG 응답을 받지 못했습니다.", new RuntimeException()));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            Throwable thrown = catchThrowable(() -> paymentUseCase.pay(aCardPayInfo(orderNumber)));

            // then
            Payment pendingPayment = findPaymentOf(orderNumber);
            Order awaitingOrder = findOrder(orderNumber);
            int heldStock = findStockOf(product);

            assertAll(
                    () -> assertThat(thrown).isNotInstanceOf(CoreException.class),
                    () -> assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(pendingPayment.getFailureReason()).isNull(),
                    () -> assertThat(awaitingOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(heldStock).isEqualTo(initialStock - ORDER_QUANTITY)
            );
        }

        @Test
        @DisplayName("포인트 결제는 PG를 부르지 않는다")
        void doesNotCallPg_whenMethodIsPoint() {
            // given
            aChargedPoint(ORDER_AMOUNT);
            String orderNumber = anAwaitingPaymentOrderNumber();

            // when
            paymentUseCase.pay(aPayInfo(orderNumber));

            // then
            verifyNoInteractions(paymentGateway);
        }
    }
}
