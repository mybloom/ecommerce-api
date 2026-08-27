package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderV1Controller.class)
class OrderV1ControllerTest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";
    private static final String HEADER_OF_IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderUseCase orderUseCase;

    private String aRequestBody(Long productId, Integer quantity) throws Exception {
        return objectMapper.writeValueAsString(new OrderV1Dto.PlaceOrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, quantity))));
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class PlaceOrder {

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsMissing() throws Exception {
            // given
            String idempotencyKey = "3f8a1c2e-0001";
            long productId = 7L;
            int orderQuantity = 2;

            String requestBody = aRequestBody(productId, orderQuantity);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenIdempotencyKeyHeaderIsMissing() throws Exception {
            // given
            long memberId = 1L;
            long productId = 7L;
            int orderQuantity = 2;

            String requestBody = aRequestBody(productId, orderQuantity);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenItemsAreEmpty() throws Exception {
            // given
            long memberId = 1L;
            String idempotencyKey = "3f8a1c2e-0001";

            String emptyItemsBody = objectMapper.writeValueAsString(
                    new OrderV1Dto.PlaceOrderRequest(List.of()));

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emptyItemsBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("주문 수량이 0이면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenQuantityIsZero() throws Exception {
            // given
            long memberId = 1L;
            String idempotencyKey = "3f8a1c2e-0001";
            long productId = 7L;
            int invalidQuantity = 0;

            String requestBody = aRequestBody(productId, invalidQuantity);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }
}
