package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandUseCaseDto;

public class BrandV1Dto {

    public record GetBrandResponse(Long brandId, String name, String description) {
        public static GetBrandResponse from(BrandUseCaseDto.GetBrandResult result) {
            return new GetBrandResponse(result.brandId(), result.name(), result.description());
        }
    }
}
