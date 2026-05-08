package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductUseCaseTest {

    private final ProductUseCase productUseCase;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductUseCaseTest(ProductUseCase productUseCase, ProductRepository productRepository,
                              BrandRepository brandRepository, DatabaseCleanUp databaseCleanUp) {
        this.productUseCase = productUseCase;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("getProduct")
    class GetProduct {

        @Test
        @DisplayName("ON_SALE 상품 조회 시 상품과 브랜드 정보가 반환된다")
        void returnsProductResult_whenProductIsOnSale() {
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));

            ProductUseCaseDto.GetProductResult result = productUseCase.getProduct(
                    new ProductUseCaseDto.GetProductInfo(savedProduct.getId()));

            assertAll(
                    () -> assertThat(result.productId()).isEqualTo(savedProduct.getId()),
                    () -> assertThat(result.name()).isEqualTo(ProductFixture.DEFAULT_NAME),
                    () -> assertThat(result.brandId()).isEqualTo(savedBrand.getId()),
                    () -> assertThat(result.brandName()).isEqualTo(BrandFixture.DEFAULT_NAME)
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductDoesNotExist() {
            long nonExistedProductId = Long.MAX_VALUE;

            assertThatThrownBy(() -> productUseCase.getProduct(
                    new ProductUseCaseDto.GetProductInfo(nonExistedProductId)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("OFF_SALE 상품 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductIsOffSale() {
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStatus(savedBrand.getId(), ProductStatus.OFF_SALE));

            assertThatThrownBy(() -> productUseCase.getProduct(
                    new ProductUseCaseDto.GetProductInfo(savedProduct.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("HIDDEN 상품 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductIsHidden() {
            Brand savedBrand = brandRepository.save(BrandFixture.aBrand());
            Product savedProduct = productRepository.save(
                    ProductFixture.aProductForBrandWithStatus(savedBrand.getId(), ProductStatus.HIDDEN));

            assertThatThrownBy(() -> productUseCase.getProduct(
                    new ProductUseCaseDto.GetProductInfo(savedProduct.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("소속 브랜드가 ACTIVE가 아니면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandIsInactive() {
            BrandStatus inactiveOfBrandStatus = BrandStatus.INACTIVE;
            Brand savedBrand = brandRepository.save(BrandFixture.aBrandWithStatus(inactiveOfBrandStatus));
            Product savedProduct = productRepository.save(ProductFixture.aProductForBrand(savedBrand.getId()));

            assertThatThrownBy(() -> productUseCase.getProduct(
                    new ProductUseCaseDto.GetProductInfo(savedProduct.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
