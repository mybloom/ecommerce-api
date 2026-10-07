import { ApiClient } from '../core/ApiClient';
import { expect, test } from '../core/fixtures';
import { TEST_CARD } from '../core/testCards';

/** docker/system-test/wiremock/mappings/pg-approval-accepted.json 이 돌려주는 거래 키 */
const STUBBED_TRANSACTION_KEY = '20260101:TR:wiremock';

test.describe('카드 결제 — 접수 후 콜백 (설계서 3.3)', () => {
  // 설계서 3.3 TC-PAY-040: PENDING 결제(Given)에 SUCCESS 콜백(When) → 200, APPROVED, PAID(Then)
  test('TC-PAY-040 PG 가 SUCCESS 콜백을 주문 총액으로 보내면 결제는 APPROVED, 주문은 PAID 가 된다 @smoke @regression @REQ-PAY-03', async ({
    // Given: 새 회원, 공용 상품 (core/fixtures.ts)
    member,
    sharedProduct,
    orderClient,
    paymentClient,
    db,
  }) => {
    // 준비 단계라 expect 대신 dataOf: 실패하면 검증 실패가 아니라 예외로 멈춘다
    const order = await test.step('Given: 주문한다', async () =>
      ApiClient.dataOf(await orderClient.placeOrder(member.id, [{ productId: sharedProduct.productId, quantity: 2 }])),
    );

    // 전제 조건 확인 (TC-PAY-030 내용). 여기서 실패하면 콜백까지 가지 못한 것
    await test.step('Given: 카드 결제를 요청하면 PG 접수 후 PENDING 으로 응답한다', async () => {
      const result = await paymentClient.pay(member.id, {
        orderNumber: order.orderNumber,
        method: 'CARD',
        ...TEST_CARD,
      });

      expect(result.status).toBe(200);
      expect(result.body.data).toMatchObject({
        orderNumber: order.orderNumber,
        method: 'CARD',
        status: 'PENDING',
        amount: order.totalAmount,
      });
    });

    await test.step('When: PG 대신 SUCCESS 콜백을 주문 총액으로 보낸다', async () => {
      const result = await paymentClient.sendPgCallback({
        transactionKey: STUBBED_TRANSACTION_KEY,
        orderId: order.orderNumber,
        status: 'SUCCESS',
        amount: order.totalAmount,
      });

      // 성격상 Then. 위치는 아직 고민 중
      expect(result.status).toBe(200);
    });

    // 주문 상세 API 가 없어 DB 로 확인 (D-2)
    await test.step('Then: 결제는 APPROVED, 주문은 PAID 다', async () => {
      const payment = await db.findPaymentByOrderNumber(order.orderNumber);
      expect(payment).toMatchObject({
        method: 'CARD',
        status: 'APPROVED',
        amount: order.totalAmount,
        transactionKey: STUBBED_TRANSACTION_KEY,
      });

      const savedOrder = await db.findOrder(order.orderNumber);
      expect(savedOrder?.status).toBe('PAID');
    });
  });
});
