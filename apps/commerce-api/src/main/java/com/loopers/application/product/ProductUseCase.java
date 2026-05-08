package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.BrandServiceDto;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductUseCase {

    private final ProductService productService;
    private final BrandService brandService;

    public ProductUseCaseDto.GetProductResult getProduct(ProductUseCaseDto.GetProductInfo info) {
        Product product = productService.retrieve(info.toCommand());

        Brand brand = brandService.retrieve(new BrandServiceDto.RetrieveCommand(product.getBrandId()));

        return ProductUseCaseDto.GetProductResult.from(product, brand);
    }
}
