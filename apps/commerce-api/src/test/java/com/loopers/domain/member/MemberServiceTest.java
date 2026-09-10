package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fixture.MemberFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder);
    }

    @DisplayName("회원 가입 시,")
    @Nested
    class Register {
        @DisplayName("회원 등록 시 Member 저장이 수행된다.")
        @Test
        void saveMember_whenCreateSucceeds() {
            String loginId = "testId";
            MemberServiceDto.RegisterCommand command = MemberFixture.aRegisterCommandWithLoginId(loginId);

            when(memberRepository.existsByLoginId(loginId)).thenReturn(false);
            when(memberRepository.existsByEmail(command.email())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("ENCODED_PASSWORD");
            when(memberRepository.save(any(Member.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

            Member member = memberService.register(command);

            verify(memberRepository).save(any(Member.class));
            assertThat(member.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("이미 존재하는 이메일로 회원 등록 시, CONFLICT 예외가 발생하고 저장은 수행되지 않는다.")
        @Test
        void throwException_whenEmailIsAlreadyExist() {
            // given
            String duplicateEmail = "dup@test.com";
            MemberServiceDto.RegisterCommand command = MemberFixture.aRegisterCommandWithEmail(duplicateEmail);

            when(memberRepository.existsByLoginId(command.loginId())).thenReturn(false);
            when(memberRepository.existsByEmail(duplicateEmail)).thenReturn(true);

            // when
            CoreException exception = assertThrows(CoreException.class, () -> memberService.register(command));

            // then
            assertAll(
                    () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                    () -> verify(memberRepository, never()).save(any(Member.class))
            );
        }

        @DisplayName("이미 존재하는 로그인 ID 로 회원 등록 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwException_whenMemberIsAlreadyExist() {
            String duplicateLoginId = "dupMember";
            MemberServiceDto.RegisterCommand command = MemberFixture.aRegisterCommandWithLoginId(duplicateLoginId);
            when(memberRepository.existsByLoginId(duplicateLoginId)).thenReturn(true);

            CoreException exception = assertThrows(CoreException.class, () -> {
                memberService.register(command);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(memberRepository, never()).save(any(Member.class));
        }
    }

    @DisplayName("회원 프로필 조회 시,")
    @Nested
    class GetMemberProfile {
        @DisplayName("회원이 존재하면 MemberProfile 을 반환한다.")
        @Test
        void returnMemberProfile_whenMemberExists() {
            long memberId = 1L;
            Member member = MemberFixture.aSavedMember(memberId);
            when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

            MemberProfile result = memberService.getMemberProfile( new MemberServiceDto.GetMemberCommand(memberId));

            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(MemberFixture.DEFAULT_LOGIN_ID);
        }

        @DisplayName("회원이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwException_whenMemberNotFound() {
            Long memberId = Long.MAX_VALUE;
            when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

            CoreException exception = assertThrows(CoreException.class, () -> {
                memberService.getMemberProfile(new MemberServiceDto.GetMemberCommand(memberId));
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
