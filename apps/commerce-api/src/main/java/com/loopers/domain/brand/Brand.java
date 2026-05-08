package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.util.Objects.requireNonNull;

@Getter
@Entity
@Table(name = "brand")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrandStatus status;

    public static Brand create(String name, String description) {
        Brand brand = new Brand();

        brand.name = requireNonNull(name);
        brand.description = description;
        brand.status = BrandStatus.ACTIVE;

        return brand;
    }

    public void deactivate() {
        this.status = BrandStatus.INACTIVE;
    }

    public void withdraw() {
        this.status = BrandStatus.WITHDRAWN;
    }

    public boolean isVisibleToUser() {
        return status == BrandStatus.ACTIVE;
    }
}
