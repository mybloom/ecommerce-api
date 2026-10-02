package com.loopers.domain.payment;

/**
 * PG에 카드 승인을 요청하는 포트.
 * <p>
 * 반환값은 <b>접수 결과이지 승인 결과가 아니다.</b> 최종 결과는 콜백이 정한다 (참고: Payment-003).
 * 실패는 세 예외 타입 중 하나로 번역해 던진다 (참고: Payment-010).
 */
public interface PaymentGateway {

    PaymentGatewayDto.Approval requestApproval(PaymentGatewayDto.ApprovalCommand command);
}
