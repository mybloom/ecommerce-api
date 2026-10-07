import { ApiClient, ApiResult } from '../core/ApiClient';

export type PointBalanceResponse = { memberId: number; balance: number };

export class PointClient {
  constructor(private readonly api: ApiClient) {}

  charge(memberId: number, amount: number): Promise<ApiResult<PointBalanceResponse>> {
    return this.api.post('/api/v1/points/charge', { memberId, data: { amount } });
  }

  retrieve(memberId: number): Promise<ApiResult<PointBalanceResponse>> {
    return this.api.get('/api/v1/points', { memberId });
  }
}
