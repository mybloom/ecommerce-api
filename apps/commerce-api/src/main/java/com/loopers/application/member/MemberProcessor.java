package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.point.PointService;
import com.loopers.domain.point.PointServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입의 트랜잭션 경계. 회원과 초기 포인트가 함께 커밋되거나 함께 롤백된다.
 * <p>
 * UNIQUE 위반은 커밋 시점에 터지므로 트랜잭션 <b>밖</b>인 {@link MemberUseCase}에서만 잡을 수 있다.
 */
@RequiredArgsConstructor
@Component
public class MemberProcessor {

    private final MemberService memberService;
    private final PointService pointService;

    @Transactional
    public Member register(MemberUseCaseDto.RegisterInfo info) {
        Member member = memberService.register(info.toCommand());

        pointService.createInitialPoint(new PointServiceDto.CreateInitialCommand(member.getId()));

        return member;
    }
}
