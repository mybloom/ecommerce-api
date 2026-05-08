package com.loopers.application.point;

import com.loopers.domain.member.MemberService;
import com.loopers.domain.member.MemberServiceDto;
import com.loopers.domain.point.PointService;
import com.loopers.domain.point.PointServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PointUseCase {

    private final PointService pointService;
    private final MemberService memberService;

    @Transactional
    public PointUseCaseDto.ChargeResult charge(PointUseCaseDto.ChargeInfo info) {
        memberService.getMember(new MemberServiceDto.GetMemberCommand(info.memberId()));

        PointServiceDto.ChargeQuery query = pointService.charge(info.toCommand());

        return PointUseCaseDto.ChargeResult.from(query);
    }

    public PointUseCaseDto.RetrieveResult retrieve(PointUseCaseDto.RetrieveInfo info) {
        PointServiceDto.RetrieveQuery query = pointService.retrieve(info.toCommand());

        return PointUseCaseDto.RetrieveResult.from(query);
    }
}
