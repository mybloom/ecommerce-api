import { request } from '@playwright/test';
import { env } from './core/env';
import { createPool } from './db/connection';
import { seedProduct } from './db/seed';

/**
 * 실행마다 한 번 돈다.
 * 1. WireMock 매핑을 파일(docker/system-test/wiremock/mappings) 상태로 되돌리고 요청 기록을 비운다
 * 2. 공용 상품을 만든다. 재고를 검증하지 않는 테스트가 함께 쓰므로 재고를 넉넉히 둔다
 */
export default async function globalSetup(): Promise<void> {
  const wiremock = await request.newContext({ baseURL: env.pgBaseUrl });
  const resets = [
    { method: 'POST', path: '/__admin/mappings/reset' },
    { method: 'DELETE', path: '/__admin/requests' },
  ];
  for (const { method, path } of resets) {
    const response = await wiremock.fetch(path, { method });
    if (!response.ok()) {
      throw new Error(`WireMock 초기화 실패: ${method} ${path} → ${response.status()}. ${env.pgBaseUrl} 에 떠 있는지 확인하세요.`);
    }
  }
  await wiremock.dispose();

  const pool = createPool({ readOnly: false });
  try {
    const product = await seedProduct(pool, { name: 'e2e-shared-product', price: 10_000, stock: 1_000_000 });
    // 워커는 globalSetup 이 바꾼 환경변수를 물려받는다
    process.env.E2E_SHARED_PRODUCT = JSON.stringify(product);
  } finally {
    await pool.end();
  }
}
