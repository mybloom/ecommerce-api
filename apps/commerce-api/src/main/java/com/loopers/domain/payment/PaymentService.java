package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 결제를 접수해 승인대기 상태로 저장한다. orderId UNIQUE 위반은 여기서 잡지 않는다 —
     * 커밋 시점에 터지므로 트랜잭션 밖에서만 잡을 수 있다 (참고: PaymentUseCase).
     */
    @Transactional
    public Payment request(PaymentServiceDto.RequestCommand command) {
        Payment payment = Payment.request(
                command.orderId(), command.memberId(), command.method(), command.amount());

        return paymentRepository.save(payment);
    }

    /**
     * 콜백은 주문번호로만 대상을 특정하므로 주문에서 결제로 찾아가는 경로가 필요하다 (참고: UC-2).
     */
    @Transactional(readOnly = true)
    public Payment findByOrderId(PaymentServiceDto.FindByOrderIdCommand command) {
        return paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    @Transactional
    public Payment approve(PaymentServiceDto.ApproveCommand command) {
        Payment payment = findExistingPayment(command.paymentId());
        payment.approve(command.transactionKey());

        return payment;
    }

    @Transactional
    public Payment fail(PaymentServiceDto.FailCommand command) {
        Payment payment = findExistingPayment(command.paymentId());
        payment.fail(command.reason());

        return payment;
    }

    private Payment findExistingPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }
}
