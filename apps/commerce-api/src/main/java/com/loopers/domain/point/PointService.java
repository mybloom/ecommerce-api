package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public PointServiceDto.RetrieveQuery retrieve(PointServiceDto.RetrieveCommand command) {
        Point point = pointRepository.findByMemberId(command.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return new PointServiceDto.RetrieveQuery(
                command.memberId(),
                point.getBalance().getAmount()
        );
    }
}
