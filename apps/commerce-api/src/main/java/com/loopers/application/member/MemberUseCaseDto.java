package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberProfile;
import com.loopers.domain.member.MemberServiceDto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MemberUseCaseDto {
    public record RegisterInfo(
            String loginId,
            String email,
            LocalDate birthDate,
            Gender gender,
            String password
    ) {
        public MemberServiceDto.RegisterCommand toCommand() {
            return new MemberServiceDto.RegisterCommand(
                    loginId,
                    email,
                    birthDate,
                    MemberServiceDto.Gender.valueOf(gender.name()),
                    password
            );
        }
    }

    public record RegisterResult(
            Long memberId,
            String loginId,
            String email,
            LocalDate birthDate,
            Gender gender
    ) {
        public static RegisterResult from(Member member) {
            return new RegisterResult(
                    member.getId(),
                    member.getLoginId(),
                    member.getEmail().getAddress(),
                    member.getBirthDate(),
                    MemberUseCaseDto.Gender.valueOf(member.getGender().name())
            );
        }
    }

    public record GetMemberProfileInfo(
            Long id
    ) {
        public MemberServiceDto.GetMemberCommand toCommand() {
            return new MemberServiceDto.GetMemberCommand(id);
        }
    }

    public record GetMemberProfileResult(
            Long id,
            String loginId,
            String email,
            LocalDate birthDate,
            Gender gender,
            LocalDateTime registerDate
    ) {
        public static GetMemberProfileResult from(MemberProfile memberProfile) {
            return new GetMemberProfileResult(
                    memberProfile.getId(),
                    memberProfile.getLoginId(),
                    memberProfile.getEmail().getAddress(),
                    memberProfile.getBirthDate(),
                    MemberUseCaseDto.Gender.valueOf(memberProfile.getGender().name()),
                    memberProfile.getCreatedAt()
            );
        }
    }

        public enum Gender {
            MALE, FEMALE
        }
    }
