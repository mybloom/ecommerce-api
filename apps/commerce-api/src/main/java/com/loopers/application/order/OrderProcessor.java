package com.loopers.application.order;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.BrandServiceDto;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderServiceDto;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 접수(T1)·확정(T2)·확정 실패 처리(T3)를 각각 독립 트랜잭션으로 수행한다 (참고: Order-004).
 * UseCase에 트랜잭션이 없으므로 이 컴포넌트의 메서드 하나하나가 곧 트랜잭션 경계다.
 */
@RequiredArgsConstructor
@Component
public class OrderProcessor {

    private final OrderService orderService;
    private final ProductService productService;
    private final BrandService brandService;

    /**
     * T1. 접수 — 커밋된다. 이 시점의 주문은 라인도 금액도 없다.
     */
    @Transactional
    public Order accept(OrderServiceDto.DraftCommand command) {
        return orderService.draft(command);
    }

    /**
     * T2. 확정 — 커밋된다. 실패하면 재고 차감분도 함께 롤백되므로 별도 보상이 필요 없다.
     */
    @Transactional
    public Order confirm(Long orderId, List<OrderUseCaseDto.OrderItemInfo> items) {
        List<OrderLine> lines = reserveStock(items);

        return orderService.confirm(new OrderServiceDto.ConfirmCommand(orderId, lines));
    }

    /**
     * T3. 확정 실패 처리 — T2가 실패했을 때만, 별도로 커밋된다. 주문 상태만 바꾼다.
     */
    @Transactional
    public void markOrderFailed(Long orderId) {
        orderService.markOrderFailed(new OrderServiceDto.MarkOrderFailedCommand(orderId));
    }

    /**
     * 주문 항목을 주문 라인으로 바꾸면서 재고를 확보한다. T2 트랜잭션 안에서만 호출된다.
     */
    private List<OrderLine> reserveStock(List<OrderUseCaseDto.OrderItemInfo> items) {
        // 같은 상품이 여러 항목으로 오면 하나로 합산.
        // TreeMap이라 순회가 productId 오름차순이 된다 — 잠금 순서를 고정해 데드락을 막는다 (참고: Order-006)
        Map<Long, Integer> quantityByProductId = new TreeMap<>();
        for (OrderUseCaseDto.OrderItemInfo item : items) {
            quantityByProductId.merge(item.productId(), item.quantity(), Integer::sum);
        }

        List<OrderLine> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            // todo: 비관적 락을 걸고 읽는다. 락은 T2가 끝날 때까지 유지된다 (참고: Order-006) -> 이 방식이 베스트인가?
            // 판매중이 아니면 여기서 NOT_FOUND (참고: Order-009)
            Product product = productService.retrieveForUpdate(new ProductServiceDto.RetrieveCommand(productId));

            // 소속 브랜드가 ACTIVE인지 확인. 비활성이면 NOT_FOUND
            brandService.retrieve(new BrandServiceDto.RetrieveCommand(product.getBrandId()));

            // 재고 검사 및 차감. 부족하면 CONFLICT (참고: Order-005)
            product.decreaseStock(quantity);

            // 상품명과 단가를 이 시점에 복사해 둔다. 이후 상품이 바뀌어도 주문 내용은 그대로다 (참고: Order-001)
            lines.add(OrderLine.of(product, quantity));
        }

        return lines;
    }
}
