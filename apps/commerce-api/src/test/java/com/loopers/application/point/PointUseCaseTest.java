package com.loopers.application.point;


import com.loopers.application.member.MemberUseCase;
import com.loopers.application.member.MemberUseCaseDto;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.point.PointFixture;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class PointUseCaseTest {

    private final PointUseCase pointUseCase;
    private final MemberUseCase memberUseCase;
    private final MemberRepository memberRepository;
    private final PointRepository pointRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public PointUseCaseTest(PointUseCase pointUseCase,
                            MemberUseCase memberUseCase,
                            MemberRepository memberRepository,
                            PointRepository pointRepository,
                            DatabaseCleanUp databaseCleanUp) {
        this.pointUseCase = pointUseCase;
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
    @DisplayName("charge")
    class Charge {

        @Test
        @DisplayName("첫 충전 시 잔액이 충전 금액과 같다")
        void charge_first_time() {
            Long memberId = aMemberWithInitialPoint();
            long chargeAmount = 1000L;

            PointUseCaseDto.ChargeResult result =
                    pointUseCase.charge(PointFixture.aChargeInfo(memberId, chargeAmount));

            assertThat(result.balance()).isEqualTo(chargeAmount);
        }

        @Test
        @DisplayName("충전 이력이 있는 회원이 포인트를 충전하면 잔액이 증가한다")
        void charge_accumulates() {
            long seedAmount = 1000L;
            Long memberId = aMemberWithSeedPoint(seedAmount);
            long chargeAmount = 500L;

            PointUseCaseDto.ChargeResult result =
                    pointUseCase.charge(PointFixture.aChargeInfo(memberId, chargeAmount));

            assertThat(result.balance()).isEqualTo(seedAmount + chargeAmount);
        }

        @Test
        @DisplayName("존재하지 않는 회원이 충전을 시도하면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenMemberDoesNotExist() {
            long nonExistentMemberId = 9999L;
            PointUseCaseDto.ChargeInfo info = PointFixture.aChargeInfo(nonExistentMemberId, 500L);

            assertThatThrownBy(() -> pointUseCase.charge(info))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("retrieve")
    class Retrieve {

        @Test
        @DisplayName("존재하는 회원의 포인트를 조회하면 잔액이 반환된다")
        void retrieve_success() {
            MemberUseCaseDto.RegisterResult registerResult = memberUseCase.register(MemberFixture.aRegisterInfo());
            Long existedMemberId = registerResult.memberId();
            Long expectedBalance = 500L;
            pointUseCase.charge(PointFixture.aChargeInfo(existedMemberId, expectedBalance));

            PointUseCaseDto.RetrieveResult result = pointUseCase.retrieve(PointFixture.aRetrieveInfo(existedMemberId));

            assertAll(
                    () -> assertThat(result.memberId()).isEqualTo(existedMemberId),
                    () -> assertThat(result.balance()).isEqualTo(expectedBalance)
            );
        }
    }

    // ===== Test Fixtures =====

    /**
     * 회원 + 초기 포인트(잔액 0)까지 영속화하고 memberId 반환.
     * 호출 후 영속성 컨텍스트는 비워진 상태.
     */
    private Long aMemberWithInitialPoint() {
        Member member = memberRepository.save(MemberFixture.aMember());
        pointRepository.save(PointFixture.anInitialPoint(member.getId()));

        return member.getId();
    }

    /**
     * 회원 + 시드 잔액 충전까지 영속화하고 memberId 반환.
     * 호출 후 영속성 컨텍스트는 비워진 상태.
     */
    private Long aMemberWithSeedPoint(long seedAmount) {
        Member member = memberRepository.save(MemberFixture.aMember());
        pointRepository.save(PointFixture.aPointWithBalance(member.getId(), seedAmount));

        return member.getId();
    }
}
