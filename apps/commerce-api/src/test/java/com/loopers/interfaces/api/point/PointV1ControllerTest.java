package com.loopers.interfaces.api.point;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.point.PointUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PointV1Controller.class)
class PointV1ControllerTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private PointUseCase pointUseCase;

    @Autowired
    public PointV1ControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Nested
    @DisplayName("POST /api/v1/points/charge")
    class Charge {
        private static final String ENDPOINT = "/api/v1/points/charge";

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400을 응답한다")
        void returns400_whenMemberIdHeaderMissing() throws Exception {
            PointV1Dto.ChargeRequest invalidRequest = new PointV1Dto.ChargeRequest(100L);

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("충전 금액이 없으면 400을 응답한다")
        void returns400_whenAmountIsNull() throws Exception {
            // given
            Long missingAmount = null;
            PointV1Dto.ChargeRequest invalidRequest = new PointV1Dto.ChargeRequest(missingAmount);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header("X-MEMBER-ID", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/points")
    class Retrieve {
        private static final String ENDPOINT = "/api/v1/points";

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400을 응답한다")
        void returns400_whenMemberIdHeaderMissing() throws Exception {
            mockMvc.perform(get(ENDPOINT))
                    .andExpect(status().isBadRequest());
        }
    }
}
