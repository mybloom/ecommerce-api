package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderUseCase;
import com.loopers.application.order.OrderUseCaseDto;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        @DisplayName("정상 요청이면 접수된 주문 정보를 반환한다")
        void returnsPlacedOrder_whenRequestIsValid() throws Exception {
            // given
            long memberId = 1L;
            String idempotencyKey = "3f8a1c2e-0001";
            long productId = 7L;
            int orderQuantity = 2;

            String orderNumber = "20260826-A3F9K2QP";
            long totalAmount = 200_000L;
            OrderUseCaseDto.PlaceOrderResult result = new OrderUseCaseDto.PlaceOrderResult(
                    orderNumber, OrderStatus.AWAITING_PAYMENT, totalAmount, ZonedDateTime.now());
            when(orderUseCase.place(any(OrderUseCaseDto.PlaceOrderInfo.class))).thenReturn(result);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aRequestBody(productId, orderQuantity)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                    .andExpect(jsonPath("$.data.status").value(OrderStatus.AWAITING_PAYMENT.name()))
                    .andExpect(jsonPath("$.data.totalAmount").value(totalAmount))
                    .andExpect(jsonPath("$.data.orderedAt").exists());
        }

        @Test
        @DisplayName("응답에 내부 식별자 id를 담지 않는다")
        void doesNotExposeInternalId() throws Exception {
            // given
            long memberId = 1L;
            String idempotencyKey = "3f8a1c2e-0001";
            long productId = 7L;
            int orderQuantity = 2;

            OrderUseCaseDto.PlaceOrderResult result = new OrderUseCaseDto.PlaceOrderResult(
                    "20260826-A3F9K2QP", OrderStatus.AWAITING_PAYMENT, 200_000L, ZonedDateTime.now());
            when(orderUseCase.place(any(OrderUseCaseDto.PlaceOrderInfo.class))).thenReturn(result);

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aRequestBody(productId, orderQuantity)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").doesNotExist());
        }

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsMissing() throws Exception {
            // given
            String idempotencyKey = "3f8a1c2e-0001";
            long productId = 7L;
            int orderQuantity = 2;

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aRequestBody(productId, orderQuantity)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenIdempotencyKeyHeaderIsMissing() throws Exception {
            // given
            long memberId = 1L;
            long productId = 7L;
            int orderQuantity = 2;

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aRequestBody(productId, orderQuantity)))
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

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aRequestBody(productId, invalidQuantity)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("주문할 수 없는 상품이면 404 Not Found를 반환한다")
        void returnsNotFound_whenProductIsNotOrderable() throws Exception {
            // given
            long memberId = 1L;
            String idempotencyKey = "3f8a1c2e-0001";
            long productId = 7L;
            int orderQuantity = 2;

            when(orderUseCase.place(any(OrderUseCaseDto.PlaceOrderInfo.class)))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aRequestBody(productId, orderQuantity)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("재고가 부족하면 409 Conflict를 반환한다")
        void returnsConflict_whenStockIsNotEnough() throws Exception {
            // given
            long memberId = 1L;
            String idempotencyKey = "3f8a1c2e-0001";
            long productId = 7L;
            int orderQuantity = 2;

            when(orderUseCase.place(any(OrderUseCaseDto.PlaceOrderInfo.class)))
                    .thenThrow(new CoreException(ErrorType.CONFLICT, "재고가 부족합니다."));

            // when & then
            mockMvc.perform(post(ENDPOINT)
                            .header(HEADER_OF_MEMBER_ID, memberId)
                            .header(HEADER_OF_IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(aRequestBody(productId, orderQuantity)))
                    .andExpect(status().isConflict());
        }
    }
}
