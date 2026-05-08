package com.loopers.domain.brand;

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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    private BrandService brandService;

    @Mock
    private BrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandRepository);
    }

    @Nested
    @DisplayName("retrieve")
    class Retrieve {

        @Test
        @DisplayName("ACTIVE 브랜드가 존재하면 Brand를 반환한다")
        void returnsBrand_whenBrandIsActive() {
            Long brandId = BrandFixture.DEFAULT_BRAND_ID;
            Brand brand = BrandFixture.aSavedBrand(brandId);
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

            Brand result = brandService.retrieve(new BrandServiceDto.RetrieveCommand(brandId));

            assertAll(
                    () -> assertThat(result.getId()).isEqualTo(brandId),
                    () -> assertThat(result.getName()).isEqualTo(BrandFixture.DEFAULT_NAME),
                    () -> assertThat(result.getDescription()).isEqualTo(BrandFixture.DEFAULT_DESCRIPTION)
            );
        }

        @Test
        @DisplayName("브랜드가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandDoesNotExist() {
            Long brandId = Long.MAX_VALUE;
            when(brandRepository.findById(brandId)).thenReturn(Optional.empty());

            CoreException exception = assertThrows(CoreException.class, () ->
                    brandService.retrieve(new BrandServiceDto.RetrieveCommand(brandId))
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("INACTIVE 브랜드 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandIsInactive() {
            Long brandId = 2L;
            Brand brand = BrandFixture.aSavedBrandWithStatus(brandId, BrandStatus.INACTIVE);
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

            CoreException exception = assertThrows(CoreException.class, () ->
                    brandService.retrieve(new BrandServiceDto.RetrieveCommand(brandId))
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("WITHDRAWN 브랜드 조회 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandIsWithdrawn() {
            Long brandId = 3L;
            Brand brand = BrandFixture.aSavedBrandWithStatus(brandId, BrandStatus.WITHDRAWN);
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

            CoreException exception = assertThrows(CoreException.class, () ->
                    brandService.retrieve(new BrandServiceDto.RetrieveCommand(brandId))
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
