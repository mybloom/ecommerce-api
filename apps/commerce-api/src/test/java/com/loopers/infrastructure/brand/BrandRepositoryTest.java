package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
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
class BrandRepositoryTest {

    private final BrandRepository brandRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandRepositoryTest(BrandRepository brandRepository, DatabaseCleanUp databaseCleanUp) {
        this.brandRepository = brandRepository;
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
        @DisplayName("저장된 브랜드를 ID로 조회하면 해당 브랜드가 반환된다")
        void returnsBrand_whenBrandExists() {
            Brand saved = brandRepository.save(BrandFixture.aBrand());

            Optional<Brand> result = brandRepository.findById(saved.getId());

            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().getId()).isEqualTo(saved.getId()),
                    () -> assertThat(result.get().getName()).isEqualTo(BrandFixture.DEFAULT_NAME),
                    () -> assertThat(result.get().getDescription()).isEqualTo(BrandFixture.DEFAULT_DESCRIPTION),
                    () -> assertThat(result.get().getStatus()).isEqualTo(BrandStatus.ACTIVE)
            );
        }

        @Test
        @DisplayName("INACTIVE 브랜드도 ID로 조회할 수 있다")
        void returnsBrand_whenBrandIsInactive() {
            Brand saved = brandRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));

            Optional<Brand> result = brandRepository.findById(saved.getId());

            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().getStatus()).isEqualTo(BrandStatus.INACTIVE)
            );
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional이 반환된다")
        void returnsEmpty_whenBrandDoesNotExist() {
            Optional<Brand> result = brandRepository.findById(Long.MAX_VALUE);

            assertThat(result).isEmpty();
        }
    }
}
