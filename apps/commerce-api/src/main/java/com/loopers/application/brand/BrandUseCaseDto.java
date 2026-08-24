package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandServiceDto;

public class BrandUseCaseDto {

    public record GetBrandInfo(Long brandId) {
        public BrandServiceDto.RetrieveCommand toCommand() {
            return new BrandServiceDto.RetrieveCommand(brandId);
        }
    }

    public record GetBrandResult(Long brandId, String name, String description) {
        public static GetBrandResult from(Brand brand) {
            return new GetBrandResult(brand.getId(), brand.getName(), brand.getDescription());
        }
    }
}
