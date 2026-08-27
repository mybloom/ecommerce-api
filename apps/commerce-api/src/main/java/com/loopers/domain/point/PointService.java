package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PointService {
    private final PointRepository pointRepository;

    public Point createInitialPoint(PointServiceDto.CreateInitialCommand command) {
        if (pointRepository.findByMemberId(command.memberId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT,
                    "이미 Point가 존재합니다. memberId=" + command.memberId());
        }

        return pointRepository.save(Point.createInitial(command));
    }

    public PointServiceDto.ChargeQuery charge(PointServiceDto.ChargeCommand command) {
        Point point = pointRepository.findByMemberId(command.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        point.charge(command.amount());

        return new PointServiceDto.ChargeQuery(
                command.memberId(),
                command.amount().getAmount(),
                point.getBalance().getAmount()
        );
    }

    /**
     * 포인트 결제에서 호출한다. 잔액이 부족하면 Point가 CONFLICT를 던진다 (참고: 07_payment.md UC-1).
     */
    @Transactional
    public void use(PointServiceDto.UseCommand command) {
        Point point = pointRepository.findByMemberId(command.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        point.use(command.amount());
    }

    public PointServiceDto.RetrieveQuery retrieve(PointServiceDto.RetrieveCommand command) {
        Point point = pointRepository.findByMemberId(command.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return new PointServiceDto.RetrieveQuery(
                command.memberId(),
                point.getBalance().getAmount()
        );
    }
}
