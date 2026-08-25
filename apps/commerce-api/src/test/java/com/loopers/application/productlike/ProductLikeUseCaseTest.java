package com.loopers.application.productlike;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.productlike.ProductLike;
import com.loopers.infrastructure.productlike.ProductLikeJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fixture.MemberFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductLikeUseCaseTest {

    private final ProductLikeUseCase productLikeUseCase;
    private final MemberRepository memberRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductLikeJpaRepository productLikeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductLikeUseCaseTest(ProductLikeUseCase productLikeUseCase, MemberRepository memberRepository,
                                  BrandRepository brandRepository, ProductRepository productRepository,
                                  ProductLikeJpaRepository productLikeJpaRepository, DatabaseCleanUp databaseCleanUp) {
        this.productLikeUseCase = productLikeUseCase;
        this.memberRepository = memberRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.productLikeJpaRepository = productLikeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("like")
    class Like {

        @Test
        @DisplayName("ON_SALE 상품에 처음 좋아요하면 좋아요가 저장되고 좋아요 수가 1이 된다")
        void savesLikeAndIncreasesLikeCount_whenFirstLike() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));
            int initialLikeCount = 5;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), initialLikeCount));

            ProductLikeUseCaseDto.LikeResult result = productLikeUseCase.like(
                    new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId()));

            ProductLike savedLike = productLikeJpaRepository.findAll().get(0);
            assertAll(
                    () -> assertThat(result.isDuplicatedRequest()).isFalse(),
                    () -> assertThat(productLikeJpaRepository.count()).isEqualTo(1),
                    () -> assertThat(savedLike.getMemberId()).isEqualTo(savedMember.getId()),
                    () -> assertThat(savedLike.getProductId()).isEqualTo(savedProduct.getId()),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow().getLikeCount())
                            .isEqualTo(initialLikeCount + 1)
            );
        }

        @Test
        @DisplayName("같은 상품에 두 번 좋아요하면 중복으로 표시되고 좋아요 수는 그대로다")
        void marksAsDuplicatedAndKeepsLikeCount_whenLikedTwice() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int initialLikeCount = 5;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), initialLikeCount));
            ProductLikeUseCaseDto.LikeInfo info =
                    new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId());
            productLikeUseCase.like(info);

            ProductLikeUseCaseDto.LikeResult result = productLikeUseCase.like(info);

            assertAll(
                    () -> assertThat(result.isDuplicatedRequest()).isTrue(),
                    () -> assertThat(productLikeJpaRepository.count()).isEqualTo(1),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow().getLikeCount())
                            .isEqualTo(initialLikeCount + 1)
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductDoesNotExist() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Long nonExistentProductId = Long.MAX_VALUE;

            assertThatThrownBy(() -> productLikeUseCase.like(
                    new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), nonExistentProductId)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("OFF_SALE 상품이면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductIsOffSale() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStatus(savedBrand.getId(), ProductStatus.OFF_SALE));

            assertThatThrownBy(() -> productLikeUseCase.like(
                    new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("HIDDEN 상품이면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductIsHidden() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStatus(savedBrand.getId(), ProductStatus.HIDDEN));

            assertThatThrownBy(() -> productLikeUseCase.like(
                    new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("소속 브랜드가 ACTIVE가 아니면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandIsInactive() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));

            assertThatThrownBy(() -> productLikeUseCase.like(
                    new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("like - 동시 요청")
    class ConcurrentLike {

        @Test
        @DisplayName("같은 회원이 동시에 두 번 좋아요해도 좋아요는 1건이고 좋아요 수도 1이다")
        void keepsSingleLike_whenSameMemberLikesConcurrently() throws InterruptedException {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int initialLikeCount = 5;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), initialLikeCount));
            ProductLikeUseCaseDto.LikeInfo info =
                    new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId());
            int concurrentRequestCount = 2;
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentRequestCount);
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequestCount);

            for (int i = 0; i < concurrentRequestCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        productLikeUseCase.like(info);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertAll(
                    () -> assertThat(failures).isEmpty(),
                    () -> assertThat(productLikeJpaRepository.count()).isEqualTo(1),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow().getLikeCount())
                            .isEqualTo(initialLikeCount + 1)
            );
        }
    }

    @Nested
    @DisplayName("unlike")
    class Unlike {

        @Test
        @DisplayName("좋아요한 상품을 취소하면 좋아요가 삭제되고 좋아요 수가 1 감소한다")
        void deletesLikeAndDecreasesLikeCount_whenLiked() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int initialLikeCount = 5;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), initialLikeCount));
            productLikeUseCase.like(new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId()));

            ProductLikeUseCaseDto.UnlikeResult result = productLikeUseCase.unlike(
                    new ProductLikeUseCaseDto.UnlikeInfo(savedMember.getId(), savedProduct.getId()));

            assertAll(
                    () -> assertThat(result.isDuplicatedRequest()).isFalse(),
                    () -> assertThat(productLikeJpaRepository.count()).isEqualTo(0),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow().getLikeCount())
                            .isEqualTo(initialLikeCount)
            );
        }

        @Test
        @DisplayName("좋아요하지 않은 상품을 취소하면 중복으로 표시되고 좋아요 수는 그대로다")
        void marksAsDuplicatedAndKeepsLikeCount_whenNotLiked() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int initialLikeCount = 5;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), initialLikeCount));

            ProductLikeUseCaseDto.UnlikeResult result = productLikeUseCase.unlike(
                    new ProductLikeUseCaseDto.UnlikeInfo(savedMember.getId(), savedProduct.getId()));

            assertAll(
                    () -> assertThat(result.isDuplicatedRequest()).isTrue(),
                    () -> assertThat(productRepository.findById(savedProduct.getId()).orElseThrow().getLikeCount())
                            .isEqualTo(initialLikeCount)
            );
        }

        @Test
        @DisplayName("OFF_SALE 상품도 좋아요를 취소할 수 있다")
        void unlikes_whenProductIsOffSale() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int initialLikeCount = 5;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), initialLikeCount));
            productLikeUseCase.like(new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId()));
            savedProduct.putOffSale();
            productRepository.save(savedProduct);

            ProductLikeUseCaseDto.UnlikeResult result = productLikeUseCase.unlike(
                    new ProductLikeUseCaseDto.UnlikeInfo(savedMember.getId(), savedProduct.getId()));

            assertAll(
                    () -> assertThat(result.isDuplicatedRequest()).isFalse(),
                    () -> assertThat(productLikeJpaRepository.count()).isEqualTo(0)
            );
        }

        @Test
        @DisplayName("소속 브랜드가 INACTIVE여도 좋아요를 취소할 수 있다")
        void unlikes_whenBrandIsInactive() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            int initialLikeCount = 5;
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithLikeCount(savedBrand.getId(), initialLikeCount));
            productLikeUseCase.like(new ProductLikeUseCaseDto.LikeInfo(savedMember.getId(), savedProduct.getId()));
            savedBrand.deactivate();
            brandRepository.save(savedBrand);

            ProductLikeUseCaseDto.UnlikeResult result = productLikeUseCase.unlike(
                    new ProductLikeUseCaseDto.UnlikeInfo(savedMember.getId(), savedProduct.getId()));

            assertAll(
                    () -> assertThat(result.isDuplicatedRequest()).isFalse(),
                    () -> assertThat(productLikeJpaRepository.count()).isEqualTo(0)
            );
        }
    }
}
