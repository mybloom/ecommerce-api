package com.loopers.domain.point;

import com.loopers.application.point.PointUseCaseDto;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class PointFixture {
    public static final long DEFAULT_MEMBER_ID = 1L;

    /**
     * 기본 memberId로 잔액 0인 Point를 생성한다.
     */
    public static Point anInitialPoint() {
        return anInitialPoint(DEFAULT_MEMBER_ID);
    }

    /**
     * 지정한 memberId로 잔액 0인 Point를 생성한다.
     */
    public static Point anInitialPoint(long memberId) {
        return Point.createInitial(new PointServiceDto.CreateInitialCommand(memberId));
    }

    /**
     * 기본 memberId로 지정한 잔액을 가진 Point를 생성한다.
     * balance는 1 이상이어야 한다 (Point.charge의 정책).
     */
    public static Point aPointWithBalance(long balance) {
        return aPointWithBalance(DEFAULT_MEMBER_ID, balance);
    }

    /**
     * 지정한 memberId와 잔액으로 Point를 생성한다.
     */
    public static Point aPointWithBalance(long memberId, long balance) {
        Point point = anInitialPoint(memberId);
        point.charge(Money.of(balance));
        return point;
    }

    /**
     * CreateInitialCommand 헬퍼.
     */
    public static PointServiceDto.CreateInitialCommand anInitialCommand() {
        return anInitialCommand(DEFAULT_MEMBER_ID);
    }

    public static PointServiceDto.CreateInitialCommand anInitialCommand(Long memberId) {
        return new PointServiceDto.CreateInitialCommand(memberId);
    }

    /**
     * ChargeCommand 헬퍼.
     */
    public static PointServiceDto.ChargeCommand aChargeCommand(Long memberId, Money amount) {
        return new PointServiceDto.ChargeCommand(memberId, amount);
    }

    /**
     * RetrieveCommand 헬퍼.
     */
    public static PointServiceDto.RetrieveCommand aRetrieveCommand(Long memberId) {
        return new PointServiceDto.RetrieveCommand(memberId);
    }

    /**
     * ChargeInfo 헬퍼.
     */
    public static PointUseCaseDto.ChargeInfo aChargeInfo(Long memberId, Long amount) {
        return new PointUseCaseDto.ChargeInfo(memberId, amount);
    }

    /**
     * RetrieveInfo 헬퍼.
     */
    public static PointUseCaseDto.RetrieveInfo aRetrieveInfo(Long memberId) {
        return new PointUseCaseDto.RetrieveInfo(memberId);
    }
}
