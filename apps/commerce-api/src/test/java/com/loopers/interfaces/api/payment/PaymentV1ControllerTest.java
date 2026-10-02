package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.payment.PaymentUseCase;
import com.loopers.application.payment.PaymentUseCaseDto;
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
    private static final String CALLBACK_ENDPOINT = "/api/v1/payments/pg/callback";
    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentUseCase paymentUseCase;

    private String aRequestBody(String orderNumber, PaymentUseCaseDto.PaymentMethod method) throws Exception {
        return objectMapper.writeValueAsString(
                new PaymentV1Dto.PayRequest(orderNumber, method, null, null));
    }

    private String aCardRequestBody(String orderNumber, PaymentUseCaseDto.CardType cardType, String cardNo) throws Exception {
        return objectMapper.writeValueAsString(
                new PaymentV1Dto.PayRequest(orderNumber, PaymentUseCaseDto.PaymentMethod.CARD, cardType, cardNo));
    }

    @Nested
    @DisplayName("POST /api/v1/payments")
    class Pay {

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsMissing() throws Exception {
            // given
            String orderNumber = "20260827-A3F9K2QP";

            String requestBody = aRequestBody(orderNumber, PaymentUseCaseDto.PaymentMethod.POINT);

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

            String requestBody = aRequestBody(blankOrderNumber, PaymentUseCaseDto.PaymentMethod.POINT);

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
            String unsupportedMethod = "BANK_TRANSFER";

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

        @Test
        @DisplayName("카드 결제인데 카드 종류가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenCardTypeIsMissingForCardPayment() throws Exception {
            // given
            long memberId = 1L;
            String orderNumber = "20260827-A3F9K2QP";
            String cardNo = "1234-5678-9814-1451";

            String requestBody = aCardRequestBody(orderNumber, null, cardNo);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("카드 결제인데 카드 번호가 비어 있으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenCardNoIsBlankForCardPayment() throws Exception {
            // given
            long memberId = 1L;
            String orderNumber = "20260827-A3F9K2QP";
            String blankCardNo = "";

            String requestBody = aCardRequestBody(orderNumber, PaymentUseCaseDto.CardType.SAMSUNG, blankCardNo);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("카드 번호 형식이 어긋나면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenCardNoFormatIsInvalid() throws Exception {
            // given
            long memberId = 1L;
            String orderNumber = "20260827-A3F9K2QP";
            String malformedCardNo = "1234-5678";

            String requestBody = aCardRequestBody(orderNumber, PaymentUseCaseDto.CardType.SAMSUNG, malformedCardNo);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("지원하지 않는 카드 종류면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenCardTypeIsNotSupported() throws Exception {
            // given
            long memberId = 1L;
            String orderNumber = "20260827-A3F9K2QP";
            String unsupportedCardType = "LOTTE";

            String requestBody = """
                    {"orderNumber": "%s", "method": "CARD", "cardType": "%s", "cardNo": "1234-5678-9814-1451"}
                    """.formatted(orderNumber, unsupportedCardType);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/pg/callback")
    class PgCallback {

        /**
         * 호출자가 PG라 이 엔드포인트는 X-MEMBER-ID를 요구하지 않는다 (참고: UC-2).
         */
        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없어도 400을 반환하지 않는다")
        void doesNotRejectRequest_whenMemberIdHeaderIsMissing() throws Exception {
            // given
            String requestBody = """
                    {"transactionKey": "20260828:TR:0a8ec1", "orderId": "20260827-A3F9K2QP",
                     "status": "SUCCESS", "amount": 5000}
                    """;

            // when & then
            mockMvc.perform(post(CALLBACK_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("거래 식별자가 비어 있으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenTransactionKeyIsBlank() throws Exception {
            // given
            String blankTransactionKey = "";

            String requestBody = """
                    {"transactionKey": "%s", "orderId": "20260827-A3F9K2QP",
                     "status": "SUCCESS", "amount": 5000}
                    """.formatted(blankTransactionKey);

            // when & then
            mockMvc.perform(post(CALLBACK_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("승인 금액이 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenAmountIsMissing() throws Exception {
            // given
            String requestBody = """
                    {"transactionKey": "20260828:TR:0a8ec1", "orderId": "20260827-A3F9K2QP",
                     "status": "SUCCESS"}
                    """;

            // when & then
            mockMvc.perform(post(CALLBACK_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PG가 알려온 상태가 지원하지 않는 값이면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenStatusIsNotSupported() throws Exception {
            // given
            String unsupportedStatus = "CANCELED";

            String requestBody = """
                    {"transactionKey": "20260828:TR:0a8ec1", "orderId": "20260827-A3F9K2QP",
                     "status": "%s", "amount": 5000}
                    """.formatted(unsupportedStatus);

            // when & then
            mockMvc.perform(post(CALLBACK_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }
}
