package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandUseCase {

    private final BrandService brandService;

    public BrandUseCaseDto.GetBrandResult getBrand(BrandUseCaseDto.GetBrandInfo info) {
        Brand brand = brandService.retrieve(info.toCommand());

        return BrandUseCaseDto.GetBrandResult.from(brand);
    }
}
