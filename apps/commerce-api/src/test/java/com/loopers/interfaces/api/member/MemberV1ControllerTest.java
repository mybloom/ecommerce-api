package com.loopers.interfaces.api.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.member.MemberUseCase;
import com.loopers.support.fixture.MemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


@WebMvcTest(MemberV1Controller.class)
class MemberV1ControllerTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private MemberUseCase memberUseCase;

    @Autowired
    public MemberV1ControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Nested
    @DisplayName("POST /api/v1/members")
    class Register {
        // ─── RequestDTO @Valid 검증 ─────────────────────────────
        @Test
        @DisplayName("회원가입 요청에 loginId가 블랭크이면 400 Bad Request")
        void register_validation_blank_loginId() throws Exception {
            MemberV1Dto.RegisterRequest invalidRequest = MemberFixture.aRegisterRequestWithLoginId("");

            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("회원가입 요청 비밀번호가 4자 미만이면 400 Bad Request")
        void register_validation_short_password() throws Exception {
            MemberV1Dto.RegisterRequest invalidRequest = MemberFixture.aRegisterRequestWithPassword("pwd");

            mockMvc.perform(post("/api/v1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/members/me")
    class GetMemberProfile {

        // ─── 헤더 추출 정책 ──────────────────────────
        @Test
        @DisplayName("내 정보 조회 시 X-MEMBER-ID 헤더가 없으면 400 Bad Request")
        void me_missing_header() throws Exception {
            mockMvc.perform(get("/api/v1/members/me"))
                    .andExpect(status().isBadRequest());
        }
    }
}
