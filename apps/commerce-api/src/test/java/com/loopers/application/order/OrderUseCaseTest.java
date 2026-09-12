package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.order.IdempotencyKey;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderUseCaseTest {

    private Member member;
    private Brand brand;

    private final OrderUseCase orderUseCase;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderUseCaseTest(OrderUseCase orderUseCase, OrderRepository orderRepository,
                            MemberRepository memberRepository, BrandRepository brandRepository,
                            ProductRepository productRepository, DatabaseCleanUp databaseCleanUp) {
        this.orderUseCase = orderUseCase;
        this.orderRepository = orderRepository;
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

    private OrderUseCaseDto.PlaceOrderInfo anOrderInfo(String idempotencyKey, Long productId, int quantity) {
        return new OrderUseCaseDto.PlaceOrderInfo(member.getId(), idempotencyKey,
                List.of(new OrderUseCaseDto.OrderItemInfo(productId, quantity)));
    }

    private Order findOrder(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(IdempotencyKey.of(idempotencyKey)).orElseThrow();
    }

    private int findStockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity().getValue();
    }

    @Nested
    @DisplayName("place - 정상 흐름")
    class Place {

        @Test
        @DisplayName("판매중인 상품을 주문하면 재고를 차감하고 결제 대기 상태로 확정된다")
        void confirmsOrderAndDecreasesStock() {
            // given
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            int orderQuantity = 2;
            Long expectedTotalAmount = ProductFixture.DEFAULT_PRICE.getAmount() * orderQuantity;
            String idempotencyKey = UUID.randomUUID().toString();

            OrderUseCaseDto.PlaceOrderInfo info =
                    anOrderInfo(idempotencyKey, savedProduct.getId(), orderQuantity);

            // when
            OrderUseCaseDto.PlaceOrderResult result = orderUseCase.place(info);

            // then
            int remainingStock = findStockOf(savedProduct);

            assertAll(
                    () -> assertThat(result.status()).isEqualTo(OrderUseCaseDto.OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(result.totalAmount()).isEqualTo(expectedTotalAmount),
                    () -> assertThat(result.orderNumber()).isNotBlank(),
                    () -> assertThat(result.orderedAt()).isNotNull(),
                    () -> assertThat(remainingStock).isEqualTo(initialStock - orderQuantity)
            );
        }

        @Test
        @DisplayName("같은 상품이 여러 항목으로 오면 하나로 합산해 차감한다")
        void mergesItems_whenSameProductAppearsTwice() {
            // given
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            int firstQuantity = 2;
            int secondQuantity = 3;
            int totalQuantity = firstQuantity + secondQuantity;
            Long expectedTotalAmount = ProductFixture.DEFAULT_PRICE.getAmount() * totalQuantity;
            String idempotencyKey = UUID.randomUUID().toString();

            OrderUseCaseDto.PlaceOrderInfo info = new OrderUseCaseDto.PlaceOrderInfo(
                    member.getId(), idempotencyKey,
                    List.of(new OrderUseCaseDto.OrderItemInfo(savedProduct.getId(), firstQuantity),
                            new OrderUseCaseDto.OrderItemInfo(savedProduct.getId(), secondQuantity)));

            // when
            OrderUseCaseDto.PlaceOrderResult result = orderUseCase.place(info);

            // then
            int remainingStock = findStockOf(savedProduct);

            assertAll(
                    () -> assertThat(result.status()).isEqualTo(OrderUseCaseDto.OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(result.totalAmount()).isEqualTo(expectedTotalAmount),
                    () -> assertThat(remainingStock).isEqualTo(initialStock - totalQuantity)
            );
        }
    }

    @Nested
    @DisplayName("place - 멱등성")
    class Idempotency {

        @Test
        @DisplayName("같은 Idempotency-Key로 다시 요청하면 같은 주문을 반환하고 재고는 한 번만 줄어든다")
        void returnsSameOrderAndDecreasesStockOnce_whenKeyIsReused() {
            // given
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(brand.getId()));
            int initialStock = ProductFixture.DEFAULT_STOCK.getValue();
            int orderQuantity = 2;
            String sameIdempotencyKey = UUID.randomUUID().toString();

            OrderUseCaseDto.PlaceOrderInfo info = anOrderInfo(sameIdempotencyKey, savedProduct.getId(), orderQuantity);

            // when
            OrderUseCaseDto.PlaceOrderResult first = orderUseCase.place(info);
            OrderUseCaseDto.PlaceOrderResult second = orderUseCase.place(info);

            // then
            int remainingStock = findStockOf(savedProduct);

            assertAll(
                    () -> assertThat(second.orderNumber()).isEqualTo(first.orderNumber()),
                    () -> assertThat(second.status()).isEqualTo(OrderUseCaseDto.OrderStatus.AWAITING_PAYMENT),
                    () -> assertThat(first.isDuplicatedRequest()).isFalse(),
                    () -> assertThat(second.isDuplicatedRequest()).isTrue(),
                    () -> assertThat(remainingStock).isEqualTo(initialStock - orderQuantity)
            );
        }
    }

    @Nested
    @DisplayName("place - 확정 실패")
    class ConfirmFailure {

        @Test
        @DisplayName("재고가 부족하면 CONFLICT가 나고 주문은 ORDER_FAILED로 남으며 재고는 그대로다")
        void marksOrderFailedAndKeepsStock_whenStockIsNotEnough() {
            // given
            int initialStock = 1;
            int orderQuantity = 2;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStock(brand.getId(), initialStock));
            String idempotencyKey = UUID.randomUUID().toString();

            OrderUseCaseDto.PlaceOrderInfo info =
                    anOrderInfo(idempotencyKey, savedProduct.getId(), orderQuantity);

            // when
            Throwable thrown = catchThrowable(() -> orderUseCase.place(info));

            // then
            Order failedOrder = findOrder(idempotencyKey);
            int keptStock = findStockOf(savedProduct);

            assertAll(
                    () -> assertThat(thrown).isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT)),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.ORDER_FAILED),
                    () -> assertThat(keptStock).isEqualTo(initialStock)
            );
        }

        @Test
        @DisplayName("판매중이 아닌 상품을 주문하면 NOT_FOUND가 나고 주문은 ORDER_FAILED로 남는다")
        void marksOrderFailed_whenProductIsNotOnSale() {
            // given
            Product offSaleProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStatus(brand.getId(), ProductStatus.OFF_SALE));
            int orderQuantity = 1;
            String idempotencyKey = UUID.randomUUID().toString();

            OrderUseCaseDto.PlaceOrderInfo info =
                    anOrderInfo(idempotencyKey, offSaleProduct.getId(), orderQuantity);

            // when
            Throwable thrown = catchThrowable(() -> orderUseCase.place(info));

            // then
            Order failedOrder = findOrder(idempotencyKey);

            assertAll(
                    () -> assertThat(thrown).isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND)),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.ORDER_FAILED)
            );
        }

        @Test
        @DisplayName("비활성 브랜드의 상품을 주문하면 NOT_FOUND가 나고 주문은 ORDER_FAILED로 남는다")
        void marksOrderFailed_whenBrandIsInactive() {
            // given
            Brand inactiveBrand = brandRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrand(inactiveBrand.getId()));
            int orderQuantity = 1;
            String idempotencyKey = UUID.randomUUID().toString();

            OrderUseCaseDto.PlaceOrderInfo info =
                    anOrderInfo(idempotencyKey, savedProduct.getId(), orderQuantity);

            // when
            Throwable thrown = catchThrowable(() -> orderUseCase.place(info));

            // then
            Order failedOrder = findOrder(idempotencyKey);

            assertAll(
                    () -> assertThat(thrown).isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND)),
                    () -> assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.ORDER_FAILED)
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품을 주문하면 NOT_FOUND가 발생한다")
        void throwsNotFound_whenProductDoesNotExist() {
            // given
            long nonExistentProductId = Long.MAX_VALUE;
            int orderQuantity = 1;
            String idempotencyKey = UUID.randomUUID().toString();

            OrderUseCaseDto.PlaceOrderInfo info =
                    anOrderInfo(idempotencyKey, nonExistentProductId, orderQuantity);

            // when & then
            assertThatThrownBy(() -> orderUseCase.place(info))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("place - 동시 주문")
    class ConcurrentPlace {
 
        @Test
        @DisplayName("재고 1개를 두 명이 동시에 주문하면 한 건만 확정되고 재고는 음수가 되지 않는다")
        void confirmsOnlyOne_whenStockIsContended() throws InterruptedException {
            // given
            int initialStock = 1;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStock(brand.getId(), initialStock));
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
                        String idempotencyKey = UUID.randomUUID().toString();
                        orderUseCase.place(anOrderInfo(idempotencyKey, savedProduct.getId(), initialStock));
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
            int remainingStock = findStockOf(savedProduct);

            assertAll(
                    () -> assertThat(failures).hasSize(1),
                    () -> assertThat(remainingStock).isEqualTo(0)
            );
        }
    }
}
