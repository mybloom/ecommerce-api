package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.payment.PaymentUseCase;
import com.loopers.domain.payment.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentV1Controller.class)
class PaymentV1ControllerTest {

    private static final String ENDPOINT = "/api/v1/payments";
    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentUseCase paymentUseCase;

    private String aRequestBody(String orderNumber, PaymentMethod method) throws Exception {
        return objectMapper.writeValueAsString(new PaymentV1Dto.PayRequest(orderNumber, method));
    }

    @Nested
    @DisplayName("POST /api/v1/payments")
    class Pay {

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsMissing() throws Exception {
            // given
            String orderNumber = "20260827-A3F9K2QP";

            String requestBody = aRequestBody(orderNumber, PaymentMethod.POINT);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("주문번호가 비어 있으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenOrderNumberIsBlank() throws Exception {
            // given
            long memberId = 1L;
            String blankOrderNumber = "";

            String requestBody = aRequestBody(blankOrderNumber, PaymentMethod.POINT);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("결제 수단이 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMethodIsMissing() throws Exception {
            // given
            long memberId = 1L;
            String orderNumber = "20260827-A3F9K2QP";

            String requestBody = aRequestBody(orderNumber, null);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("지원하지 않는 결제 수단이면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMethodIsNotSupported() throws Exception {
            // given
            long memberId = 1L;
            String orderNumber = "20260827-A3F9K2QP";
            String unsupportedMethod = "CARD";

            String requestBody = """
                    {"orderNumber": "%s", "method": "%s"}
                    """.formatted(orderNumber, unsupportedMethod);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }
}
