package com.loopers.domain.member;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberProfile {
    private final Long id;
    private final String loginId;
    private final Email email;
    private final LocalDate birthDate;
    private final Gender gender;
    private final LocalDateTime createdAt;

    static MemberProfile of(Long id, String loginId, Email email, LocalDate birthDate, Gender gender, ZonedDateTime createdAt) {
        return new MemberProfile(
                requireNonNull(id),
                requireNonNull(loginId),
                requireNonNull(email),
                requireNonNull(birthDate),
                requireNonNull(gender),
                requireNonNull(createdAt).toLocalDateTime()
        );
    }
}
