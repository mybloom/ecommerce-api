package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderNumberTest {

    @Nested
    @DisplayName("of - 주문번호 생성 시,")
    class Of {

        @Test
        @DisplayName("yyyyMMdd-8자리 영숫자 형식이면 그 값을 가진 주문번호가 만들어진다")
        void createsOrderNumber_whenFormatIsValid() {
            // given
            String validOrderNumber = "20260827-A3F9K2QP";

            // when
            OrderNumber orderNumber = OrderNumber.of(validOrderNumber);

            // then
            assertThat(orderNumber.getValue()).isEqualTo(validOrderNumber);
        }

        @DisplayName("형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "20260827A3F9K2QP",
                "20260827-A3F9K2Q",
                "20260827-A3F9K2QPX",
                "2026082-A3F9K2QP",
                "20260827-a3f9k2qp",
                "20260827-A3F9K2Q!",
                ""
        })
        void throwsBadRequest_whenFormatIsInvalid(String invalidOrderNumber) {
            assertThatThrownBy(() -> OrderNumber.of(invalidOrderNumber))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenValueIsNull() {
            assertThatThrownBy(() -> OrderNumber.of(null))
                    .isInstanceOfSatisfying(CoreException.class, e ->
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("generate - 주문번호 채번 시,")
    class Generate {

        @Test
        @DisplayName("채번한 값은 앞 8자리가 채번 시점의 날짜이고 9번째가 구분자다")
        void startsWithToday() {
            // given
            String today = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // when
            OrderNumber orderNumber = OrderNumber.generate();

            // then
            assertAll(
                    () -> assertThat(orderNumber.getValue()).startsWith(today),
                    () -> assertThat(orderNumber.getValue().charAt(8)).isEqualTo('-')
            );
        }

        @Test
        @DisplayName("채번한 값은 of()의 형식 검증을 통과한다")
        void generatesValidFormat() {
            // when
            OrderNumber generated = OrderNumber.generate();

            // then
            OrderNumber reconstructed = OrderNumber.of(generated.getValue());

            assertThat(reconstructed).isEqualTo(generated);
        }

        @Test
        @DisplayName("천 번 연속 채번해도 서로 다른 값이 나온다")
        void generatesDistinctValues() {
            // given
            int generateCount = 1_000;

            // when
            Set<OrderNumber> orderNumbers = new HashSet<>();
            for (int i = 0; i < generateCount; i++) {
                orderNumbers.add(OrderNumber.generate());
            }

            // then
            assertThat(orderNumbers).hasSize(generateCount);
        }
    }

    @Nested
    @DisplayName("동등성 비교 시,")
    class Equality {

        @Test
        @DisplayName("같은 주문번호는 동등하고 hashCode도 같다")
        void equalsAndHashCode_whenValuesAreSame() {
            // given
            String value = "20260827-A3F9K2QP";
            OrderNumber first = OrderNumber.of(value);
            OrderNumber second = OrderNumber.of(value);

            // when & then
            assertAll(
                    () -> assertThat(first).isEqualTo(second),
                    () -> assertThat(first.hashCode()).isEqualTo(second.hashCode())
            );
        }

        @Test
        @DisplayName("다른 주문번호는 동등하지 않다")
        void notEquals_whenValuesDiffer() {
            // given
            OrderNumber first = OrderNumber.of("20260827-A3F9K2QP");
            OrderNumber second = OrderNumber.of("20260827-B4G0L3RQ");

            // when & then
            assertThat(first).isNotEqualTo(second);
        }
    }
}
