package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberProfile;
import com.loopers.domain.member.MemberService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
@Component
public class MemberUseCase {

    private final MemberProcessor memberProcessor;
    private final MemberService memberService;

    /**
     * 트랜잭션을 걸지 않는다. loginId·email UNIQUE 위반은 트랜잭션 안에서 잡을 수 없어
     * 밖인 여기서 409로 바꾼다.
     */
    public MemberUseCaseDto.RegisterResult register(MemberUseCaseDto.RegisterInfo info) {
        try {
            Member member = memberProcessor.register(info);
            return MemberUseCaseDto.RegisterResult.from(member);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID 또는 이메일입니다.");
        }
    }

    public MemberUseCaseDto.GetMemberProfileResult getMemberProfile(MemberUseCaseDto.GetMemberProfileInfo info) {
        MemberProfile memberProfile = memberService.getMemberProfile(info.toCommand());

        return MemberUseCaseDto.GetMemberProfileResult.from(memberProfile);
    }
}
