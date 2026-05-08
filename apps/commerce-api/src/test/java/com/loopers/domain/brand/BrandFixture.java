package com.loopers.domain.brand;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.util.ReflectionTestUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BrandFixture {

    public static final Long DEFAULT_BRAND_ID = 1L;
    public static final String DEFAULT_NAME = "Nike";
    public static final String DEFAULT_DESCRIPTION = "Just Do It";

    public static Brand aBrand() {
        return Brand.create(DEFAULT_NAME, DEFAULT_DESCRIPTION);
    }

    public static Brand aBrandWithStatus(BrandStatus status) {
        Brand brand = Brand.create(DEFAULT_NAME, DEFAULT_DESCRIPTION);

        if (status == BrandStatus.INACTIVE) brand.deactivate();
        else if (status == BrandStatus.WITHDRAWN) brand.withdraw();

        return brand;
    }

    public static Brand aSavedBrand(Long id) {
        Brand brand = aBrand();
        ReflectionTestUtils.setField(brand, "id", id);
        return brand;
    }

    public static Brand aSavedBrandWithStatus(Long id, BrandStatus status) {
        Brand brand = aBrandWithStatus(status);
        ReflectionTestUtils.setField(brand, "id", id);
        return brand;
    }
}
