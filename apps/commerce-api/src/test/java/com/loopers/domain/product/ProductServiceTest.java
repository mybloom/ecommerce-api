package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Nested
    @DisplayName("retrieve")
    class Retrieve {

        @Test
        @DisplayName("ON_SALE 상품이 존재하면 Product를 반환한다")
        void returnsProduct_whenProductIsOnSale() {
            Long productId = ProductFixture.DEFAULT_PRODUCT_ID;
            Product product = ProductFixture.aSavedProduct(productId);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            Product result = productService.retrieve(new ProductServiceDto.RetrieveCommand(productId));

            assertThat(result.getId()).isEqualTo(productId);
            assertThat(result.getName()).isEqualTo(ProductFixture.DEFAULT_NAME);
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductDoesNotExist() {
            Long productId = Long.MAX_VALUE;
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            CoreException exception = assertThrows(CoreException.class, () ->
                    productService.retrieve(new ProductServiceDto.RetrieveCommand(productId)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("OFF_SALE 상품 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductIsOffSale() {
            Long productId = 2L;
            Product product = ProductFixture.aSavedProductWithStatus(productId, ProductStatus.OFF_SALE);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            CoreException exception = assertThrows(CoreException.class, () ->
                    productService.retrieve(new ProductServiceDto.RetrieveCommand(productId)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("HIDDEN 상품 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductIsHidden() {
            Long productId = 3L;
            Product product = ProductFixture.aSavedProductWithStatus(productId, ProductStatus.HIDDEN);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            CoreException exception = assertThrows(CoreException.class, () ->
                    productService.retrieve(new ProductServiceDto.RetrieveCommand(productId)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("increaseLikeCount")
    class IncreaseLikeCount {

        @Test
        @DisplayName("상품의 좋아요 수를 1 증가시킨다")
        void increasesLikeCount_whenProductExists() {
            Long productId = ProductFixture.DEFAULT_PRODUCT_ID;
            int initialLikeCount = 5;
            Product product = ProductFixture.aProductForBrandWithLikeCount(
                    ProductFixture.DEFAULT_BRAND_ID, initialLikeCount);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            productService.increaseLikeCount(new ProductServiceDto.LikeCountCommand(productId));

            assertThat(product.getLikeCount()).isEqualTo(initialLikeCount + 1);
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductDoesNotExist() {
            Long nonExistentProductId = Long.MAX_VALUE;
            when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

            CoreException exception = assertThrows(CoreException.class, () ->
                    productService.increaseLikeCount(new ProductServiceDto.LikeCountCommand(nonExistentProductId)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("decreaseLikeCount")
    class DecreaseLikeCount {

        @Test
        @DisplayName("상품의 좋아요 수를 1 감소시킨다")
        void decreasesLikeCount_whenProductExists() {
            Long productId = ProductFixture.DEFAULT_PRODUCT_ID;
            int initialLikeCount = 5;
            Product product = ProductFixture.aProductForBrandWithLikeCount(
                    ProductFixture.DEFAULT_BRAND_ID, initialLikeCount);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            productService.decreaseLikeCount(new ProductServiceDto.LikeCountCommand(productId));

            assertThat(product.getLikeCount()).isEqualTo(initialLikeCount - 1);
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductDoesNotExist() {
            Long nonExistentProductId = Long.MAX_VALUE;
            when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

            CoreException exception = assertThrows(CoreException.class, () ->
                    productService.decreaseLikeCount(new ProductServiceDto.LikeCountCommand(nonExistentProductId)));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
