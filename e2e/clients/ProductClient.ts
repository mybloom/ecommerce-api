import { ApiClient, ApiResult } from '../core/ApiClient';

export type ProductStatus = 'ON_SALE' | 'OFF_SALE' | 'HIDDEN';

export type ProductResponse = {
  productId: number;
  name: string;
  description: string | null;
  representativeImage: string | null;
  brandId: number;
  brandName: string;
  price: number;
  isSoldOut: boolean;
  likeCount: number;
  status: ProductStatus;
  openedAt: string;
};

/** 상품은 조회만 있다. 생성 API 가 없어 테스트 데이터는 db/seed.ts 로 만든다 (Q-E2) */
export class ProductClient {
  constructor(private readonly api: ApiClient) {}

  getProduct(productId: number): Promise<ApiResult<ProductResponse>> {
    return this.api.get(`/api/v1/products/${productId}`);
  }
}
