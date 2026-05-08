package com.loopers.application.member;

import com.loopers.domain.member.MemberProfile;
import com.loopers.domain.point.PointService;
import com.loopers.domain.point.PointServiceDto;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@RequiredArgsConstructor
@Component
public class MemberUseCase {

    private final MemberService memberService;
    private final PointService pointService;

    @Transactional
    public MemberUseCaseDto.RegisterResult register(MemberUseCaseDto.RegisterInfo info) {
        Member member = memberService.register(info.toCommand());

        pointService.createInitialPoint(new PointServiceDto.CreateInitialCommand(member.getId()));

        return MemberUseCaseDto.RegisterResult.from(member);
    }

    public MemberUseCaseDto.GetMemberProfileResult getMemberProfile(MemberUseCaseDto.GetMemberProfileInfo info) {
        MemberProfile memberProfile = memberService.getMemberProfile(info.toCommand());

        return MemberUseCaseDto.GetMemberProfileResult.from(memberProfile);
    }
}
