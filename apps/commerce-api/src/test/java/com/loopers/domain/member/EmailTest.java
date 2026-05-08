package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.fixture.MemberFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void equality() {
        var email1 = Email.of("test@test.com");
        var email2 = Email.of("test@test.com");

        assertThat(email1).isEqualTo(email2);
    }

    @Test
    void invalidEmail() {
        assertThatThrownBy(() -> {
            Email.of("invalid-email");
        }).isInstanceOfSatisfying(CoreException.class, e ->
                assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}
