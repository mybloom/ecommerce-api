package com.loopers.support.fixture;

import com.loopers.application.member.MemberUseCaseDto;
import com.loopers.domain.member.*;
import com.loopers.interfaces.api.member.MemberV1Dto;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class MemberFixture {

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

    public static final String DEFAULT_LOGIN_ID = "testUser";
    public static final String DEFAULT_EMAIL = "test@test.com";
    public static final String DEFAULT_PASSWORD = "secret";
    public static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1990, 1, 1);
    public static final String DEFAULT_STRING_GENDER = "MALE";
    public static final MemberServiceDto.Gender DEFAULT_SERVICE_GENDER = MemberServiceDto.Gender.MALE;
    public static final MemberUseCaseDto.Gender DEFAULT_USECASE_GENDER = MemberUseCaseDto.Gender.MALE;

    // MemberV1Dto 생성
    public static MemberV1Dto.RegisterRequest aRegisterRequest() {
        return new MemberV1Dto.RegisterRequest(
                DEFAULT_LOGIN_ID,
                DEFAULT_EMAIL,
                DEFAULT_BIRTH_DATE,
                DEFAULT_STRING_GENDER,
                DEFAULT_PASSWORD
        );
    }

    public static MemberV1Dto.RegisterRequest aRegisterRequestWithLoginId(String loginId) {
        return new MemberV1Dto.RegisterRequest(
                loginId,
                DEFAULT_EMAIL,
                DEFAULT_BIRTH_DATE,
                DEFAULT_STRING_GENDER,
                DEFAULT_PASSWORD
        );
    }

    public static MemberV1Dto.RegisterRequest aRegisterRequestWithPassword(String password) {
        return new MemberV1Dto.RegisterRequest(
                DEFAULT_LOGIN_ID,
                DEFAULT_EMAIL,
                DEFAULT_BIRTH_DATE,
                DEFAULT_STRING_GENDER,
                password
        );
    }

    // MemberUseCaseDto.RegiserInfo 생성
    public static MemberUseCaseDto.RegisterInfo aRegisterInfo() {
        return new MemberUseCaseDto.RegisterInfo(
                DEFAULT_LOGIN_ID,
                DEFAULT_EMAIL,
                DEFAULT_BIRTH_DATE,
                DEFAULT_USECASE_GENDER,
                DEFAULT_PASSWORD
        );
    }

    public static MemberUseCaseDto.RegisterInfo aRegisterInfoWith(String loginId, String email) {
        return new MemberUseCaseDto.RegisterInfo(
                loginId,
                email,
                DEFAULT_BIRTH_DATE,
                DEFAULT_USECASE_GENDER,
                DEFAULT_PASSWORD
        );
    }

    // MemberServiceDto.CreateCommand 생성
    public static MemberServiceDto.RegisterCommand aRegisterCommand() {
        return new MemberServiceDto.RegisterCommand(
                DEFAULT_LOGIN_ID,
                DEFAULT_EMAIL,
                DEFAULT_BIRTH_DATE,
                DEFAULT_SERVICE_GENDER,
                DEFAULT_PASSWORD
        );
    }

    public static MemberServiceDto.RegisterCommand aRegisterCommandWithEmail(String email) {
        return new MemberServiceDto.RegisterCommand(
                DEFAULT_LOGIN_ID,
                email,
                DEFAULT_BIRTH_DATE,
                DEFAULT_SERVICE_GENDER,
                DEFAULT_PASSWORD
        );
    }

    public static MemberServiceDto.RegisterCommand aRegisterCommandWithLoginId(String loginId) {
        return new MemberServiceDto.RegisterCommand(
                loginId,
                DEFAULT_EMAIL,
                DEFAULT_BIRTH_DATE,
                DEFAULT_SERVICE_GENDER,
                DEFAULT_PASSWORD
        );
    }

    // Member 엔티티 생성
    public static Member aMember() {
        return Member.register(aRegisterCommand(), PASSWORD_ENCODER);
    }

    public static Member aMemberWithEmail(String email) {
        return Member.register(aRegisterCommandWithEmail(email), PASSWORD_ENCODER);
    }

    public static Member aMemberWithLoginId(String loginId) {
        return Member.register(aRegisterCommandWithLoginId(loginId), PASSWORD_ENCODER);
    }

    public static Member aMemberWithLoginIdAndEmail(String loginId, String email) {
        return Member.register(
                new MemberServiceDto.RegisterCommand(
                        loginId,
                        email,
                        DEFAULT_BIRTH_DATE,
                        DEFAULT_SERVICE_GENDER,
                        DEFAULT_PASSWORD
                ),
                PASSWORD_ENCODER
        );
    }

    // Member 엔티티 생성(JPA 저장된 상태)
    public static Member aSavedMember(Long id) {
        Member member = aMember();
        injectPersistenceFields(member, id);

        return member;
    }

    private static void injectPersistenceFields(Member member, Long id) {
        ZonedDateTime now = ZonedDateTime.now();
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")));
        ReflectionTestUtils.setField(member, "updatedAt", ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")));
    }
}
