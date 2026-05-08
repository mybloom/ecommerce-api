package com.loopers.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrandTest {

    @Nested
    @DisplayName("isVisibleToUser")
    class IsVisibleToUser {

        @Test
        @DisplayName("ACTIVE 상태이면 true를 반환한다")
        void returnsTrue_whenStatusIsActive() {
            Brand brand = BrandFixture.aBrandWithStatus(BrandStatus.ACTIVE);

            assertThat(brand.isVisibleToUser()).isTrue();
        }

        @Test
        @DisplayName("INACTIVE 상태이면 false를 반환한다")
        void returnsFalse_whenStatusIsInactive() {
            Brand brand = BrandFixture.aBrandWithStatus(BrandStatus.INACTIVE);

            assertThat(brand.isVisibleToUser()).isFalse();
        }

        @Test
        @DisplayName("WITHDRAWN 상태이면 false를 반환한다")
        void returnsFalse_whenStatusIsWithdrawn() {
            Brand brand = BrandFixture.aBrandWithStatus(BrandStatus.WITHDRAWN);

            assertThat(brand.isVisibleToUser()).isFalse();
        }
    }
}
