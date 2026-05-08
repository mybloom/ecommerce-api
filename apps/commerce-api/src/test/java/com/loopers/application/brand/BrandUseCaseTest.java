package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandFixture;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
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
class BrandUseCaseTest {

    private final BrandUseCase brandUseCase;
    private final BrandRepository brandRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandUseCaseTest(BrandUseCase brandUseCase, BrandRepository brandRepository, DatabaseCleanUp databaseCleanUp) {
        this.brandUseCase = brandUseCase;
        this.brandRepository = brandRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("getBrand")
    class GetBrand {

        @Test
        @DisplayName("ACTIVE 브랜드 조회 시 브랜드 정보가 반환된다")
        void returnsBrandInfo_whenBrandIsActive() {
            Brand saved = brandRepository.save(BrandFixture.aBrand());

            BrandUseCaseDto.GetBrandResult result = brandUseCase.getBrand(new BrandUseCaseDto.GetBrandInfo(saved.getId()));

            assertAll(
                    () -> assertThat(result.brandId()).isEqualTo(saved.getId()),
                    () -> assertThat(result.name()).isEqualTo(BrandFixture.DEFAULT_NAME),
                    () -> assertThat(result.description()).isEqualTo(BrandFixture.DEFAULT_DESCRIPTION)
            );
        }

        @Test
        @DisplayName("존재하지 않는 브랜드 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandDoesNotExist() {
            assertThatThrownBy(() -> brandUseCase.getBrand(new BrandUseCaseDto.GetBrandInfo(Long.MAX_VALUE)))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("INACTIVE 브랜드 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandIsInactive() {
            Brand saved = brandRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE));

            assertThatThrownBy(() -> brandUseCase.getBrand(new BrandUseCaseDto.GetBrandInfo(saved.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("WITHDRAWN 브랜드 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandIsWithdrawn() {
            Brand saved = brandRepository.save(BrandFixture.aBrandWithStatus(BrandStatus.WITHDRAWN));

            assertThatThrownBy(() -> brandUseCase.getBrand(new BrandUseCaseDto.GetBrandInfo(saved.getId())))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
