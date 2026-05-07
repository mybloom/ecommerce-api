package com.loopers.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemberProfileTest {
    private static final Long DEFAULT_ID = 1L;
    private static final String DEFAULT_LOGIN_ID = "testId";
    private static final Email DEFAULT_EMAIL = Email.of("test@test.com");
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(2000, 1, 1);
    private static final Gender DEFAULT_GENDER = Gender.MALE;
    private static final ZonedDateTime DEFAULT_CREATED_AT = ZonedDateTime.of(
            2024, 1, 1, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")
    );

    private static MemberProfile aMemberProfile() {
        return MemberProfile.of(
                DEFAULT_ID, DEFAULT_LOGIN_ID, DEFAULT_EMAIL,
                DEFAULT_BIRTH_DATE, DEFAULT_GENDER, DEFAULT_CREATED_AT
        );
    }

    @Nested
    @DisplayName("equals & hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("모든 필드가 같으면 두 MemberProfile은 같다")
        void equal_whenAllFieldsAreSame() {
            MemberProfile a = aMemberProfile();
            MemberProfile b = aMemberProfile();

            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("같은 값이면 hashCode도 같다")
        void hashCode_consistentWithEquals() {
            MemberProfile a = aMemberProfile();
            MemberProfile b = aMemberProfile();

            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("모든 필드가 인자대로 매핑된다")
        void mapsAllFieldsAsGiven() {
            MemberProfile profile = aMemberProfile();

            assertThat(profile.getId()).isEqualTo(DEFAULT_ID);
            assertThat(profile.getLoginId()).isEqualTo(DEFAULT_LOGIN_ID);
            assertThat(profile.getEmail()).isEqualTo(DEFAULT_EMAIL);
            assertThat(profile.getBirthDate()).isEqualTo(DEFAULT_BIRTH_DATE);
            assertThat(profile.getGender()).isEqualTo(DEFAULT_GENDER);
        }
    }
}
