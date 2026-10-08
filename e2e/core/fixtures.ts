import { randomUUID } from 'node:crypto';
import { test as base } from '@playwright/test';
import { MemberClient } from '../clients/MemberClient';
import { OrderClient } from '../clients/OrderClient';
import { PaymentClient } from '../clients/PaymentClient';
import { PointClient } from '../clients/PointClient';
import { ProductClient } from '../clients/ProductClient';
import { createPool } from '../db/connection';
import { ReadOnlyDb } from '../db/ReadOnlyDb';
import { SeedProductOptions, SeededProduct, seedProduct } from '../db/seed';
import { ApiClient } from './ApiClient';

type TestFixtures = {
  memberClient: MemberClient;
  pointClient: PointClient;
  productClient: ProductClient;
  orderClient: OrderClient;
  paymentClient: PaymentClient;
  /** 테스트마다 새로 가입한 회원. 병렬 실행에서 데이터가 섞이지 않게 회원을 공유하지 않는다 */
  member: { id: number; loginId: string };
  /** global-setup.ts 가 만든 공용 상품. 재고를 검증하는 테스트는 쓰지 않는다 */
  sharedProduct: SeededProduct;
  /** 이 테스트 전용 상품을 만든다. 재고를 정확히 검증할 때 쓴다 */
  seedProduct: (options?: SeedProductOptions) => Promise<SeededProduct>;
};

type WorkerFixtures = {
  db: ReadOnlyDb;
};

export const test = base.extend<TestFixtures, WorkerFixtures>({
  memberClient: async ({ request }, use) => use(new MemberClient(new ApiClient(request))),
  pointClient: async ({ request }, use) => use(new PointClient(new ApiClient(request))),
  productClient: async ({ request }, use) => use(new ProductClient(new ApiClient(request))),
  orderClient: async ({ request }, use) => use(new OrderClient(new ApiClient(request))),
  paymentClient: async ({ request }, use) => use(new PaymentClient(new ApiClient(request))),

  member: async ({ memberClient }, use) => {
    // loginId 는 3~15자. 'e2e' + 12자리 무작위
    const loginId = `e2e${randomUUID().replace(/-/g, '').slice(0, 12)}`;
    const registered = ApiClient.dataOf(
      await memberClient.register({
        loginId,
        email: `${loginId}@example.com`,
        birthDate: '1990-01-01',
        gender: 'FEMALE',
        password: 'e2e-password',
      }),
    );
    await use({ id: registered.id, loginId: registered.loginId });
  },

  sharedProduct: async ({}, use) => {
    const raw = process.env.E2E_SHARED_PRODUCT;
    if (!raw) {
      throw new Error('공용 상품이 없습니다. global-setup.ts 가 실행되었는지 확인하세요.');
    }
    await use(JSON.parse(raw) as SeededProduct);
  },

  seedProduct: async ({}, use) => {
    const pool = createPool({ readOnly: false });
    await use((options) => seedProduct(pool, options));
    await pool.end();
  },

  db: [
    async ({}, use) => {
      const db = ReadOnlyDb.open();
      await use(db);
      await db.close();
    },
    { scope: 'worker' },
  ],
});

export { expect } from '@playwright/test';
