package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public Member register(MemberServiceDto.RegisterCommand command) {
        if (memberRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자입니다.");
        }

        Member member = memberRepository.save(Member.register(command, passwordEncoder));

        return member;
    }

    @Transactional(readOnly = true)
    public MemberProfile getMemberProfile(MemberServiceDto.GetMemberCommand command) {
        MemberProfile memberProfile = memberRepository.findById(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."))
                .toProfile();

        return memberProfile;
    }

    @Transactional(readOnly = true)
    public Member getMember(MemberServiceDto.GetMemberCommand command) {
        return memberRepository.findById(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
