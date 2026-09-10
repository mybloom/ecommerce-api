package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberUseCaseDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class MemberV1Dto {
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 15) String loginId,
            @NotBlank @Email String email,
            @NotNull LocalDate birthDate,
            @NotBlank String gender,
            @NotBlank @Size(min = 4, max = 50) String password) {
        public MemberUseCaseDto.RegisterInfo toInfo() {
            return new MemberUseCaseDto.RegisterInfo(
                    this.loginId,
                    this.email,
                    this.birthDate,
                    MemberUseCaseDto.Gender.valueOf(gender),
                    password
            );
        }
    }

    public record RegisterResponse(Long id, String loginId) {
        public static RegisterResponse from(MemberUseCaseDto.RegisterResult result) {
            return new RegisterResponse(
                    result.memberId(),
                    result.loginId()
            );
        }
    }

    public record GetMemberProfileResponse(
            Long id,
            String loginId,
            String email,
            LocalDate birthDate,
            String gender
    ) {
        public static GetMemberProfileResponse from(MemberUseCaseDto.GetMemberProfileResult result) {
            return new GetMemberProfileResponse(
                    result.id(),
                    result.loginId(),
                    result.email(),
                    result.birthDate(),
                    result.gender().name()
            );
        }
    }
}
