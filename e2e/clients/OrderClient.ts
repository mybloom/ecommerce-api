import { randomUUID } from 'node:crypto';
import { ApiClient, ApiResult } from '../core/ApiClient';

export type OrderStatus = 'PENDING' | 'AWAITING_PAYMENT' | 'ORDER_FAILED' | 'PAID' | 'PAYMENT_FAILED';

export type OrderItem = { productId: number; quantity: number };

export type PlaceOrderResponse = {
  orderNumber: string;
  status: OrderStatus;
  totalAmount: number;
  orderedAt: string;
  isDuplicatedRequest: boolean;
};

/** 주문 상세 조회 API 는 아직 없다 (F-7). 주문 상태는 db/ReadOnlyDb.ts 로 확인한다 (계획서 D-2) */
export class OrderClient {
  constructor(private readonly api: ApiClient) {}

  /** idempotencyKey 를 주지 않으면 요청마다 새로 만든다 */
  placeOrder(
    memberId: number,
    items: OrderItem[],
    idempotencyKey: string = randomUUID(),
  ): Promise<ApiResult<PlaceOrderResponse>> {
    return this.api.post('/api/v1/orders', {
      memberId,
      headers: { 'Idempotency-Key': idempotencyKey },
      data: { items },
    });
  }
}
