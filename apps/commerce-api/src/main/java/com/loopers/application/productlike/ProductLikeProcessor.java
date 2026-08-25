package com.loopers.application.productlike;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.BrandServiceDto;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductServiceDto;
import com.loopers.domain.productlike.ProductLikeService;
import com.loopers.domain.productlike.ProductLikeServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductLikeProcessor {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductLikeService productLikeService;

    @Transactional
    public ProductLikeServiceDto.LikeQuery like(ProductLikeServiceDto.LikeCommand command) {
        Product product = productService.retrieve(new ProductServiceDto.RetrieveCommand(command.productId()));
        brandService.retrieve(new BrandServiceDto.RetrieveCommand(product.getBrandId()));

        ProductLikeServiceDto.LikeQuery query = productLikeService.like(command);

        if (!query.duplicated()) {
            productService.increaseLikeCount(new ProductServiceDto.LikeCountCommand(command.productId()));
        }

        return query;
    }

    @Transactional
    public ProductLikeServiceDto.UnlikeQuery unlike(ProductLikeServiceDto.UnlikeCommand command) {
        ProductLikeServiceDto.UnlikeQuery query = productLikeService.unlike(command);

        if (!query.duplicated()) {
            productService.decreaseLikeCount(new ProductServiceDto.LikeCountCommand(command.productId()));
        }

        return query;
    }
}
