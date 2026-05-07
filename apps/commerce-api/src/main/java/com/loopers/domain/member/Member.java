package com.loopers.domain.member;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String loginId;

    @Embedded
    @Column(name = "email", nullable = false, unique = true)
    private Email email;

    @Column(nullable = false)
    private LocalDate birthDate;

    private Gender gender;

    private String passwordHash;

    public static Member register(MemberServiceDto.RegisterCommand command, PasswordEncoder passwordEncoder) {
        Member member = new Member();

        member.loginId = requireNonNull(command.loginId());
        member.email = Email.of(command.email());
        member.birthDate = requireNonNull(command.birthDate());
        member.gender = requireNonNull(Gender.valueOf(command.gender().name()));
        member.passwordHash = requireNonNull(passwordEncoder.encode(command.password()));

        return member;
    }

    public boolean verifyPassword(String password, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(password, this.passwordHash);
    }

    public MemberProfile toProfile() {
        return MemberProfile.of(id, loginId, email, birthDate, gender, getCreatedAt());
    }
}
