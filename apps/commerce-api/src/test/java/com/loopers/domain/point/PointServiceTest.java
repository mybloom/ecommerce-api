package com.loopers.domain.point;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.loopers.domain.point.PointFixture.*;
import static com.loopers.domain.point.PointFixture.aChargeCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class PointServiceTest {
    private PointService pointService;

    @Mock
    private PointRepository pointRepository;

    @BeforeEach
    void setUp() {
        pointService = new PointService(pointRepository);
    }

    @Nested
    @DisplayName("createInitialPoint")
    class CreateInitialPoint {

        @Test
        @DisplayName("해당 사용자가 기존 Point가 없으면 새로 생성하여 저장한다")
        void createsNewPoint_whenNotExists() {
            when(pointRepository.findByMemberId(DEFAULT_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(pointRepository.save(any(Point.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Point result = pointService.createInitialPoint(anInitialCommand());

            assertAll(
                    () -> assertThat(result.getMemberId()).isEqualTo(DEFAULT_MEMBER_ID),
                    () -> assertThat(result.getBalance().isZero()).isTrue()
            );
            verify(pointRepository).save(any(Point.class));
        }

        @Test
        @DisplayName("해당 사용자가 이미 Point가 존재하면 CONFLICT 예외가 발생한다")
        void returnsExistingPoint_whenAlreadyExists() {
            Point existing = aPointWithBalance(5_000L);
            when(pointRepository.findByMemberId(DEFAULT_MEMBER_ID))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> pointService.createInitialPoint(anInitialCommand()))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.CONFLICT));

            verify(pointRepository, never()).save(any(Point.class));
        }
    }

    @Nested
    @DisplayName("charge")
    class Charge {

        @Test
        @DisplayName("정상 충전 시 잔액이 증가한 결과를 반환한다")
        void returnsIncreasedBalance_whenChargeSucceeds() {
            Long initialAmount = 1_000L;
            Point point = aPointWithBalance(initialAmount);
            when(pointRepository.findByMemberId(DEFAULT_MEMBER_ID))
                    .thenReturn(Optional.of(point));
            Long chargeAmount = 500L;

            PointServiceDto.ChargeQuery result = pointService.charge(aChargeCommand(DEFAULT_MEMBER_ID, Money.of(chargeAmount)));

            assertAll(
                    () -> assertThat(result.memberId()).isEqualTo(DEFAULT_MEMBER_ID),
                    () -> assertThat(result.amount()).isEqualTo(chargeAmount),
                    () -> assertThat(result.balance()).isEqualTo(initialAmount + chargeAmount)
            );
        }

        @Test
        @DisplayName("해당 사용자에 대한 Point가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenPointDoesNotExist() {
            when(pointRepository.findByMemberId(DEFAULT_MEMBER_ID))
                    .thenReturn(Optional.empty());

            PointServiceDto.ChargeCommand command = aChargeCommand(DEFAULT_MEMBER_ID, Money.of(1_000L));

            assertThatThrownBy(() -> pointService.charge(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("retrieve")
    class Retrieve {

        @Test
        @DisplayName("해당 사용자에 대한 Point가 존재하면 memberId와 잔액을 담은 Query를 반환한다")
        void returnsQueryWithBalance_whenPointExists() {
            long balance = 1_000L;
            Point point = aPointWithBalance(balance);
            when(pointRepository.findByMemberId(DEFAULT_MEMBER_ID))
                    .thenReturn(Optional.of(point));

            PointServiceDto.RetrieveQuery query = pointService.retrieve(aRetrieveCommand(DEFAULT_MEMBER_ID));

            assertAll(
                    () -> assertThat(query.memberId()).isEqualTo(DEFAULT_MEMBER_ID),
                    () -> assertThat(query.balance()).isEqualTo(balance)
            );
        }

        @Test
        @DisplayName("해당 사용자에 대한 Point가 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenPointDoesNotExist() {
            when(pointRepository.findByMemberId(DEFAULT_MEMBER_ID))
                    .thenReturn(Optional.empty());

            PointServiceDto.RetrieveCommand command = aRetrieveCommand(DEFAULT_MEMBER_ID);

            assertThatThrownBy(() -> pointService.retrieve(command))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
