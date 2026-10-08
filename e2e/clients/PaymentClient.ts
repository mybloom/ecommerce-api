import { ApiClient, ApiResult } from '../core/ApiClient';
import { env } from '../core/env';

export type PaymentMethod = 'POINT' | 'CARD';
export type PaymentStatus = 'PENDING' | 'APPROVED' | 'FAILED';
export type CardType = 'SAMSUNG' | 'KB' | 'HYUNDAI';
export type PgTransactionStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

/** 결제 금액은 요청에 없다. 서버가 주문 총액을 쓴다 */
export type PayRequest = {
  orderNumber: string;
  method: PaymentMethod;
  cardType?: CardType;
  /** xxxx-xxxx-xxxx-xxxx. 테스트 카드 번호만 쓴다 (core/testCards.ts) */
  cardNo?: string;
};

export type PayResponse = {
  orderNumber: string;
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  approvedAt: string | null;
  failureReason: string | null;
};

/** PG 가 보내는 콜백. 필드 이름은 PG 규격을 따른다 — 주문번호가 orderId 다 */
export type PgCallbackRequest = {
  transactionKey: string;
  orderId: string;
  status: PgTransactionStatus;
  amount: number;
  reason?: string | null;
};

export class PaymentClient {
  constructor(private readonly api: ApiClient) {}

  pay(memberId: number, request: PayRequest): Promise<ApiResult<PayResponse>> {
    return this.api.post('/api/v1/payments', { memberId, data: request });
  }

  /** PG 대신 테스트가 콜백을 보낸다 (계획서 D-3) */
  sendPgCallback(request: PgCallbackRequest): Promise<ApiResult<null>> {
    return this.api.post(env.pgCallbackPath, { data: request });
  }
}
