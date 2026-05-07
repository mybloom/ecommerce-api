package com.loopers.domain.member;

import java.time.LocalDate;

public class MemberServiceDto {
    public record RegisterCommand(
            String loginId,
            String email,
            LocalDate birthDate,
            Gender gender,
            String password
    ) {
    }

    public record GetMemberCommand(
            Long id
    ) {
    }


    public enum Gender {
        MALE, FEMALE
    }
}
