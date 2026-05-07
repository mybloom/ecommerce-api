package com.loopers.domain.member;

import com.loopers.support.fixture.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;


class MemberTest {
    private static final PasswordEncoder PASSWORD_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(String password) {
            return password.toUpperCase();
        }

        @Override
        public boolean matches(String password, String passwordHash) {
            return encode(password).equals(passwordHash);
        }
    };

    private Member member;

    @BeforeEach
    void setup() {
        this.member = MemberFixture.aMember();
    }

    @Test
    void registerMember() {
        assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(MemberFixture.DEFAULT_LOGIN_ID),
                () -> assertThat(member.getPasswordHash()).isNotNull()
        );
    }

    //정적 분석에서 이미 걸러지므로, null 체크 정도는 테스트로 검증하지 않는다.
    @DisplayName("Member생성 시, null 체크를 한다.")
    @Test
    void constructorNullCheck() {
        assertThatThrownBy(() ->
                MemberFixture.aMemberWithLoginId(null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void verifyPassword() {
        assertThat(member.verifyPassword(MemberFixture.DEFAULT_PASSWORD, PASSWORD_ENCODER)).isTrue();
        assertThat(member.verifyPassword(MemberFixture.DEFAULT_PASSWORD + "_", PASSWORD_ENCODER)).isFalse();
    }

    @Nested
    @DisplayName("toProfile")
    class ToProfile {

        @Test
        @DisplayName("Member의 데이터가 MemberProfile에 매핑되어 반환된다")
        void mapsAllFieldsToProfile() {
            long memberId = 1L;
            Member member = MemberFixture.aSavedMember(memberId);

            MemberProfile profile = member.toProfile();

            assertAll(
                    () -> assertThat(profile).isNotNull(),
                    () -> assertThat(profile.getId()).isEqualTo(member.getId()),
                    () -> assertThat(profile.getLoginId()).isEqualTo(member.getLoginId()),
                    () -> assertThat(profile.getEmail()).isEqualTo(member.getEmail()),
                    () -> assertThat(profile.getBirthDate()).isEqualTo(member.getBirthDate()),
                    () -> assertThat(profile.getGender()).isEqualTo(member.getGender()),
                    () -> assertThat(profile.getCreatedAt()).isEqualTo(member.getCreatedAt().toLocalDateTime())
            );
        }
    }
}
