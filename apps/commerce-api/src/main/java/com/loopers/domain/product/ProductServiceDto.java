package com.loopers.domain.product;

public class ProductServiceDto {
    public record RetrieveCommand(Long productId) {}
}
