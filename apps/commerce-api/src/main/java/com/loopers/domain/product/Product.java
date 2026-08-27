package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;

@Getter
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column
    private String representativeImage;

    @Column(nullable = false)
    private Long brandId;

    @Embedded
    private Money price;

    @Embedded
    private StockQuantity stockQuantity;

    @Column(nullable = false)
    private int likeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(nullable = false)
    private ZonedDateTime openedAt;

    public static Product create(String name, String description, String representativeImage,
                                 Long brandId, Money price, StockQuantity stockQuantity,
                                 ZonedDateTime openedAt) {
        Product product = new Product();

        product.name = requireNonNull(name);
        product.description = requireNonNull(description);
        product.representativeImage = requireNonNull(representativeImage);
        product.brandId = requireNonNull(brandId);
        product.price = requireNonNull(price);
        product.stockQuantity = requireNonNull(stockQuantity);
        product.likeCount = 0;
        product.status = ProductStatus.ON_SALE;
        product.openedAt = requireNonNull(openedAt);

        return product;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void putOffSale() {
        this.status = ProductStatus.OFF_SALE;
    }

    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    public boolean isVisibleToUser() {
        return status.isVisibleToUser();
    }

    public boolean isSoldOut() {
        return stockQuantity.isSoldOut();
    }

    public void decreaseStock(int quantity) {
        if (!stockQuantity.isEnough(quantity)) {
            throw new CoreException(ErrorType.CONFLICT,
                    "재고가 부족합니다. stock=" + stockQuantity.getValue() + ", quantity=" + quantity);
        }

        this.stockQuantity = this.stockQuantity.subtract(quantity);
    }

    /**
     * 결제 실패로 확보했던 재고를 되돌린다. 복원 수량의 근거는 항상 OrderLine.quantity다 (참고: Order-007).
     */
    public void increaseStock(int quantity) {
        this.stockQuantity = this.stockQuantity.add(quantity);
    }
}
