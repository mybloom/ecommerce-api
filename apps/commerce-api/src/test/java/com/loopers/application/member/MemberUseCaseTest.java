package com.loopers.application.member;

import com.loopers.domain.point.PointService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.point.Point;
import com.loopers.domain.point.PointRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fixture.MemberFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;


@SpringBootTest
class MemberUseCaseTest {

    private final MemberUseCase memberUseCase;
    private final MemberRepository memberRepository;
    private final PointRepository pointRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoSpyBean
    private PointService pointService;

    @Autowired
    public MemberUseCaseTest(
            MemberUseCase memberUseCase,
            MemberRepository memberRepository,
            PointRepository pointRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.memberUseCase = memberUseCase;
        this.memberRepository = memberRepository;
        this.pointRepository = pointRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("회원 가입 시,")
    class Register {

        @DisplayName("회원 가입 시, 포인트가 0원으로 초기화된다.")
        @Test
        void register_initializesPointToZero() {
            MemberUseCaseDto.RegisterInfo info = MemberFixture.aRegisterInfo();

            MemberUseCaseDto.RegisterResult result = memberUseCase.register(info);

            Point savedPoint = pointRepository.findByMemberId(result.memberId())
                    .orElseThrow(() -> new AssertionError("포인트가 존재하지 않습니다."));

            assertAll(
                    () -> assertThat(result.memberId()).isNotNull(),
                    () -> assertThat(savedPoint.getBalance().isZero()).isTrue()
            );
        }

        @DisplayName("포인트 생성 실패 시, 회원도 생성되지 않는다.")
        @Test
        void doesNotCreatedMember_whenCreatePointFail() {
            MemberUseCaseDto.RegisterInfo info = MemberFixture.aRegisterInfo();
            doThrow(new CoreException(ErrorType.CONFLICT, "포인트 생성 실패"))
                    .when(pointService).createInitialPoint(any());

            assertThatThrownBy(() -> memberUseCase.register(info))
                    .isInstanceOf(CoreException.class);

            boolean existedByLoginId = memberRepository.existsByLoginId(info.loginId());
            assertThat(existedByLoginId).isFalse();
        }
    }

    @Nested
    @DisplayName("회원 조회 시,")
    class GetMemberProfile {

        @DisplayName("존재하지 않는 회원 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void me_throwsException_whenMemberNotFound() {
            long nonExistedMemberId = 999L;
            MemberUseCaseDto.GetMemberProfileInfo info = new MemberUseCaseDto.GetMemberProfileInfo(nonExistedMemberId);

            assertThatThrownBy(() -> memberUseCase.getMemberProfile(info))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하는 회원을 조회하면, 회원 정보가 반환된다.")
        @Test
        void me_existMember() {
            Member savedMember = memberRepository.save(MemberFixture.aMember());

            MemberUseCaseDto.GetMemberProfileResult result = memberUseCase.getMemberProfile(new MemberUseCaseDto.GetMemberProfileInfo(savedMember.getId()));

            assertAll(
                    () -> assertThat(result.id()).isEqualTo(savedMember.getId()),
                    () -> assertThat(result.loginId()).isEqualTo(savedMember.getLoginId())
            );
        }
    }
}
