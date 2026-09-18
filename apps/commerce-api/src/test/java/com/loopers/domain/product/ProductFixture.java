package com.loopers.domain.product;

import com.loopers.domain.shared.Money;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;

public class ProductFixture {

    public static final Long DEFAULT_PRODUCT_ID = 33L;
    public static final String DEFAULT_NAME = "Nike Air Max";
    public static final String DEFAULT_DESCRIPTION = "경량 러닝화";
    public static final String DEFAULT_IMAGE = "https://example.com/image.jpg";
    public static final Long DEFAULT_BRAND_ID = 44L;
    public static final Money DEFAULT_PRICE = Money.of(100_000L);
    public static final StockQuantity DEFAULT_STOCK = StockQuantity.of(10);
    public static final ZonedDateTime DEFAULT_OPENED_AT = ZonedDateTime.now();

    public static Product aProduct() {
        return Product.create(DEFAULT_NAME, DEFAULT_DESCRIPTION, DEFAULT_IMAGE,
                DEFAULT_BRAND_ID, DEFAULT_PRICE, DEFAULT_STOCK, DEFAULT_OPENED_AT);
    }

    public static Product aProductWithStatus(ProductStatus status) {
        Product product = aProduct();

        if (status == ProductStatus.OFF_SALE) product.putOffSale();
        else if (status == ProductStatus.HIDDEN) product.hide();
        return product;
    }

    public static Product aProductWithStock(int stock) {
        return Product.create(DEFAULT_NAME, DEFAULT_DESCRIPTION, DEFAULT_IMAGE,
                DEFAULT_BRAND_ID, DEFAULT_PRICE, StockQuantity.of(stock), DEFAULT_OPENED_AT);
    }

    public static Product aSavedProduct(Long id) {
        Product product = aProduct();

        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    public static Product aSavedProductWithStatus(Long id, ProductStatus status) {
        Product product = aProductWithStatus(status);

        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    public static Product aProductForBrand(Long brandId) {
        return Product.create(DEFAULT_NAME, DEFAULT_DESCRIPTION, DEFAULT_IMAGE,
                brandId, DEFAULT_PRICE, DEFAULT_STOCK, DEFAULT_OPENED_AT);
    }

    public static Product aProductForBrandWithLikeCount(Long brandId, int likeCount) {
        Product product = aProductForBrand(brandId);

        for (int i = 0; i < likeCount; i++) {
            product.increaseLikeCount();
        }
        return product;
    }

    public static Product aProductForBrandWithStock(Long brandId, int stock) {
        return Product.create(DEFAULT_NAME, DEFAULT_DESCRIPTION, DEFAULT_IMAGE,
                brandId, DEFAULT_PRICE, StockQuantity.of(stock), DEFAULT_OPENED_AT);
    }

    public static Product aProductForBrandWithPrice(Long brandId, Long price) {
        return Product.create(DEFAULT_NAME, DEFAULT_DESCRIPTION, DEFAULT_IMAGE,
                brandId, Money.of(price), DEFAULT_STOCK, DEFAULT_OPENED_AT);
    }

    public static Product aProductForBrandWithOpenedAt(Long brandId, ZonedDateTime openedAt) {
        return Product.create(DEFAULT_NAME, DEFAULT_DESCRIPTION, DEFAULT_IMAGE,
                brandId, DEFAULT_PRICE, DEFAULT_STOCK, openedAt);
    }

    public static Product aProductForBrandWithStatus(Long brandId, ProductStatus status) {
        Product product = aProductForBrand(brandId);
        if (status == ProductStatus.OFF_SALE) product.putOffSale();
        else if (status == ProductStatus.HIDDEN) product.hide();
        return product;
    }
}
