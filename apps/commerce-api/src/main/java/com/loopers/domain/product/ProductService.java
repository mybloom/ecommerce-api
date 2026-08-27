package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Product retrieve(ProductServiceDto.RetrieveCommand command) {
        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (!product.isVisibleToUser()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        return product;
    }

    /**
     * 재고 차감을 위해 비관적 락을 걸고 조회한다 (참고: Order-006).
     * 4단계(Infrastructure)에서 findByIdForUpdate가 생기면 본문을 채운다.
     */
    @Transactional
    public Product retrieveForUpdate(ProductServiceDto.RetrieveCommand command) {
        Product product = productRepository.findByIdForUpdate(command.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (!product.isVisibleToUser()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        return product;
    }

    @Transactional
    public void increaseLikeCount(ProductServiceDto.LikeCountCommand command) {
        findExistingProduct(command.productId()).increaseLikeCount();
    }

    @Transactional
    public void decreaseLikeCount(ProductServiceDto.LikeCountCommand command) {
        findExistingProduct(command.productId()).decreaseLikeCount();
    }

    private Product findExistingProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }
}
