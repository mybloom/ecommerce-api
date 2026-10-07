import { ApiClient } from '../core/ApiClient';
import { expect, test } from '../core/fixtures';
import { TEST_CARD } from '../core/testCards';

/** docker/system-test/wiremock/mappings/pg-approval-accepted.json 이 돌려주는 거래 키 */
const STUBBED_TRANSACTION_KEY = '20260101:TR:wiremock';

test.describe('카드 결제 — 접수 후 콜백 (설계서 3.3)', () => {
  test('TC-PAY-040 PG 가 SUCCESS 콜백을 주문 총액으로 보내면 결제는 APPROVED, 주문은 PAID 가 된다 @smoke @regression @REQ-PAY-03', async ({
    member,
    sharedProduct,
    pointClient,
    orderClient,
    paymentClient,
    db,
  }) => {
    await test.step('포인트를 충전한다', async () => {
      ApiClient.dataOf(await pointClient.charge(member.id, 100_000));
    });

    const order = await test.step('주문한다', async () =>
      ApiClient.dataOf(await orderClient.placeOrder(member.id, [{ productId: sharedProduct.productId, quantity: 2 }])),
    );

    await test.step('카드 결제를 요청하면 PG 접수 후 PENDING 으로 응답한다', async () => {
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

    await test.step('PG 대신 SUCCESS 콜백을 주문 총액으로 보낸다', async () => {
      const result = await paymentClient.sendPgCallback({
        transactionKey: STUBBED_TRANSACTION_KEY,
        orderId: order.orderNumber,
        status: 'SUCCESS',
        amount: order.totalAmount,
      });

      expect(result.status).toBe(200);
    });

    await test.step('결제는 APPROVED, 주문은 PAID 다', async () => {
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
