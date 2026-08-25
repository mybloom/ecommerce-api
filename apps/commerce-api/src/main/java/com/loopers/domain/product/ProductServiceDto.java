package com.loopers.domain.product;

public class ProductServiceDto {
    public record RetrieveCommand(Long productId) {}

    public record LikeCountCommand(Long productId) {}
}
