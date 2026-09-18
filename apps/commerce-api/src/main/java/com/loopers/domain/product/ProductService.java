package com.loopers.domain.product;

import com.loopers.domain.shared.PageResult;
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
     * 주문 경로이므로 노출되지 않는 상품은 살 수 없다 (참고: Order-009).
     */
    @Transactional
    public Product retrieveForUpdate(ProductServiceDto.RetrieveCommand command) {
        Product product = findForUpdate(command.productId());

        if (!product.isVisibleToUser()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }

        return product;
    }

    /**
     * 재고 복원을 위해 비관적 락을 걸고 조회한다. <b>노출 여부는 보지 않는다</b> (참고: Order-007).
     * <p>
     * 이미 나간 재고를 제자리에 놓는 일이라 그 상품을 지금 팔 수 있는지와 무관하다.
     * 주문 확정과 결제 실패 사이에 상품이 내려가면 retrieveForUpdate로는 복원 자체가 막힌다.
     */
    @Transactional
    public Product retrieveForRestore(ProductServiceDto.RetrieveCommand command) {
        return findForUpdate(command.productId());
    }

    private Product findForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PageResult<ProductSummary> retrieveSummaries(ProductServiceDto.RetrieveSummariesCommand command) {
        throw new UnsupportedOperationException("3단계에서 구현");
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
