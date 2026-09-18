package com.loopers.domain.productlike;

import java.util.List;

public class ProductLikeServiceDto {
    public record LikeCommand(Long memberId, Long productId) {}

    public record LikeQuery(Long memberId, Long productId, boolean duplicated) {}

    public record UnlikeCommand(Long memberId, Long productId) {}

    public record UnlikeQuery(Long memberId, Long productId, boolean duplicated) {}

    public record RetrieveLikedProductIdsCommand(Long memberId, List<Long> productIds) {}
}
