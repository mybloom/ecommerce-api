package com.loopers.infrastructure.support;

import com.loopers.domain.member.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt
            = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @Override
    public String encode(String password) {
        return bcrypt.encode(password);
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        return bcrypt.matches(password, passwordHash);
    }
}
