package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class IdempotencyKeyTest {

    @Nested
    @DisplayName("of - 멱등키 생성 시,")
    class Of {

        @Test
        @DisplayName("UUID가 아닌 키도 허용한다")
        void createsKey_whenValueIsNotUuid() {
            // given
            String nonUuidKey = "order-req-20260827-0001";

            // when
            IdempotencyKey key = IdempotencyKey.of(nonUuidKey);

            // then
            assertThat(key.getValue()).isEqualTo(nonUuidKey);
        }

        @Test
        @DisplayName("길이 상한과 같은 길이의 키는 허용한다")
        void createsKey_whenValueIsAtMaxLength() {
            // given
            String maxLengthKey = "a".repeat(IdempotencyKey.MAX_LENGTH);

            // when
            IdempotencyKey key = IdempotencyKey.of(maxLengthKey);

            // then
            assertThat(key.getValue()).hasSize(IdempotencyKey.MAX_LENGTH);
        }

        @DisplayName("비어 있거나 공백뿐이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void throwsBadRequest_whenValueIsBlank(String blankKey) {
            assertThatThrownBy(() -> IdempotencyKey.of(blankKey))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("길이 상한을 넘으면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenValueExceedsMaxLength() {
            // given
            String tooLongKey = "a".repeat(IdempotencyKey.MAX_LENGTH + 1);

            // when & then
            assertThatThrownBy(() -> IdempotencyKey.of(tooLongKey))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("동등성 비교 시,")
    class Equality {

        @Test
        @DisplayName("같은 키는 동등하고 hashCode도 같다")
        void equalsAndHashCode_whenValuesAreSame() {
            // given
            String value = "3f8a1c2e-0001";
            IdempotencyKey first = IdempotencyKey.of(value);
            IdempotencyKey second = IdempotencyKey.of(value);

            // when & then
            assertAll(
                    () -> assertThat(first).isEqualTo(second),
                    () -> assertThat(first.hashCode()).isEqualTo(second.hashCode())
            );
        }

        @Test
        @DisplayName("다른 키는 동등하지 않다")
        void notEquals_whenValuesDiffer() {
            // given
            IdempotencyKey first = IdempotencyKey.of("3f8a1c2e-0001");
            IdempotencyKey second = IdempotencyKey.of("3f8a1c2e-0002");

            // when & then
            assertThat(first).isNotEqualTo(second);
        }
    }
}
