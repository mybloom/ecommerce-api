/**
 * 테스트 전용 카드 번호. 실제 카드 번호는 쓰지 않는다 (계획서 4절).
 * 요청 기록에서는 core/ApiClient.ts 가 cardNo 를 가린다.
 */
export const TEST_CARD = {
  cardType: 'SAMSUNG',
  cardNo: '1234-5678-9814-1451',
} as const;
