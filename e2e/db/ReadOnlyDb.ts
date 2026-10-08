import { Pool, RowDataPacket } from 'mysql2/promise';
import type { OrderStatus } from '../clients/OrderClient';
import type { PaymentMethod, PaymentStatus } from '../clients/PaymentClient';
import { createPool } from './connection';

/**
 * 결과 관측용 읽기 전용 DB 헬퍼 — 블랙박스 원칙의 예외다 (계획서 D-2).
 *
 * 주문 상세 조회 API 가 없어(F-7) 결제 후 주문·결제 상태를 HTTP 로 확인할 수 없다.
 * 주문 상세 API 가 생기면 이 헬퍼 대신 OrderClient 로 확인하고, 이 파일은 지운다.
 */
export type OrderRow = { id: number; orderNumber: string; memberId: number; status: OrderStatus; totalAmount: number };

export type PaymentRow = {
  id: number;
  orderId: number;
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  transactionKey: string | null;
  failureReason: string | null;
};

export class ReadOnlyDb {
  private constructor(private readonly pool: Pool) {}

  static open(): ReadOnlyDb {
    return new ReadOnlyDb(createPool({ readOnly: true }));
  }

  async findOrder(orderNumber: string): Promise<OrderRow | undefined> {
    const [rows] = await this.pool.execute<RowDataPacket[]>(
      `SELECT id, order_number AS orderNumber, member_id AS memberId, status, total_amount AS totalAmount
       FROM orders WHERE order_number = ?`,
      [orderNumber],
    );
    return rows[0] as OrderRow | undefined;
  }

  async findPaymentByOrderNumber(orderNumber: string): Promise<PaymentRow | undefined> {
    const [rows] = await this.pool.execute<RowDataPacket[]>(
      `SELECT p.id, p.order_id AS orderId, p.method, p.status, p.amount,
              p.transaction_key AS transactionKey, p.failure_reason AS failureReason
       FROM payments p JOIN orders o ON o.id = p.order_id
       WHERE o.order_number = ?`,
      [orderNumber],
    );
    return rows[0] as PaymentRow | undefined;
  }

  async close(): Promise<void> {
    await this.pool.end();
  }
}
