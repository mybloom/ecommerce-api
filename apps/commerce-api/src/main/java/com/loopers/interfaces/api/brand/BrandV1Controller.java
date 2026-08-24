package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandUseCase;
import com.loopers.application.brand.BrandUseCaseDto;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/brands")
@RestController
public class BrandV1Controller implements BrandV1ApiSpec {

    private final BrandUseCase brandUseCase;

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.GetBrandResponse> getBrand(@PathVariable Long brandId) {
        BrandUseCaseDto.GetBrandResult result = brandUseCase.getBrand(new BrandUseCaseDto.GetBrandInfo(brandId));
        return ApiResponse.success(BrandV1Dto.GetBrandResponse.from(result));
    }
}
