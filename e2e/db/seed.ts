import { Pool, ResultSetHeader } from 'mysql2/promise';

/**
 * 브랜드·상품은 생성 API 가 없어 DB 에 직접 넣는다 (Q-E2). 이 파일만 DB 에 쓴다.
 *
 * - global-setup.ts 가 공용 상품 하나를 만든다. 재고를 검증하지 않는 테스트가 함께 쓴다
 * - 재고를 정확히 검증하는 테스트는 seedProduct() 를 테스트 안에서 불러 전용 상품을 쓴다.
 *   공용 상품은 병렬로 실행되는 다른 테스트가 재고를 바꾼다
 */
export type SeedProductOptions = {
  name?: string;
  price?: number;
  stock?: number;
};

export type SeededProduct = { productId: number; brandId: number; price: number; stock: number };

export async function seedProduct(pool: Pool, options: SeedProductOptions = {}): Promise<SeededProduct> {
  const price = options.price ?? 10_000;
  const stock = options.stock ?? 100;
  const name = options.name ?? `e2e-product-${Date.now()}`;

  const [brand] = await pool.execute<ResultSetHeader>(
    `INSERT INTO brand (name, description, status, created_at, updated_at)
     VALUES (?, 'e2e 시드', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
    [`e2e-brand-${Date.now()}`],
  );
  const [product] = await pool.execute<ResultSetHeader>(
    // amount = 가격(Money), value = 재고(StockQuantity). 임베디드 타입의 필드 이름이 컬럼 이름이 된다
    `INSERT INTO product (name, description, brand_id, amount, value, like_count, status, opened_at, created_at, updated_at)
     VALUES (?, 'e2e 시드', ?, ?, ?, 0, 'ON_SALE', UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
    [name, brand.insertId, price, stock],
  );
  return { productId: product.insertId, brandId: brand.insertId, price, stock };
}
