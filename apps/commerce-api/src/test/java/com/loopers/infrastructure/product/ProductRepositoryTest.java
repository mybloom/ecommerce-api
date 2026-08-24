package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class ProductRepositoryTest {

    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductRepositoryTest(ProductRepository productRepository, DatabaseCleanUp databaseCleanUp) {
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("저장된 상품을 ID로 조회하면 해당 상품이 반환된다")
        void returnsProduct_whenProductExists() {
            Product saved = productRepository.save(ProductFixture.aProduct());

            Optional<Product> result = productRepository.findById(saved.getId());

            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().getId()).isEqualTo(saved.getId()),
                    () -> assertThat(result.get().getName()).isEqualTo(ProductFixture.DEFAULT_NAME),
                    () -> assertThat(result.get().getStatus()).isEqualTo(ProductStatus.ON_SALE)
            );
        }

        @Test
        @DisplayName("OFF_SALE 상품도 ID로 조회할 수 있다")
        void returnsProduct_whenProductIsOffSale() {
            Product saved = productRepository.save(ProductFixture.aProductWithStatus(ProductStatus.OFF_SALE));

            Optional<Product> result = productRepository.findById(saved.getId());

            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().getStatus()).isEqualTo(ProductStatus.OFF_SALE)
            );
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional이 반환된다")
        void returnsEmpty_whenProductDoesNotExist() {
            Optional<Product> result = productRepository.findById(Long.MAX_VALUE);

            assertThat(result).isEmpty();
        }
    }
}
