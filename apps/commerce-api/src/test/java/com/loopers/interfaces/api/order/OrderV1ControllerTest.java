package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderUseCase;
import com.loopers.application.order.OrderUseCaseDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Nested
    @DisplayName("GET /api/v1/orders/{orderNumber}")
    class GetOrder {

        private static final String ORDER_NUMBER = "20260827-A3F9K2QP";
        private static final ZonedDateTime ORDERED_AT = ZonedDateTime.parse("2026-08-27T10:00:00+09:00");
        private static final ZonedDateTime PAID_AT = ZonedDateTime.parse("2026-08-27T10:05:00+09:00");

        private OrderUseCaseDto.GetOrderResult aResultWithPayment(OrderUseCaseDto.PaymentResult payment) {
            return new OrderUseCaseDto.GetOrderResult(
                    ORDER_NUMBER,
                    OrderUseCaseDto.OrderStatus.PAID,
                    20_000L,
                    ORDERED_AT,
                    PAID_AT,
                    List.of(new OrderUseCaseDto.OrderLineResult(7L, "무선 이어폰", 10_000L, 2, 20_000L)),
                    payment
            );
        }

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsMissing() throws Exception {
            // when & then
            mockMvc.perform(get(ENDPOINT + "/" + ORDER_NUMBER))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("주문을 찾을 수 없으면 404 Not Found를 반환한다")
        void returnsNotFound_whenOrderIsNotFound() throws Exception {
            // given
            long memberId = 1L;
            when(orderUseCase.getOrder(any()))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

            // when & then
            mockMvc.perform(get(ENDPOINT + "/" + ORDER_NUMBER)
                            .header(HEADER_OF_MEMBER_ID, memberId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("결제가 있으면 주문·라인·결제 정보를 응답하고 내부 식별자는 담지 않는다")
        void returnsOrderWithLinesAndPayment_whenPaymentExists() throws Exception {
            // given
            long memberId = 1L;
            OrderUseCaseDto.PaymentResult payment = new OrderUseCaseDto.PaymentResult(
                    OrderUseCaseDto.PaymentMethod.POINT, OrderUseCaseDto.PaymentStatus.APPROVED, PAID_AT, null);
            when(orderUseCase.getOrder(any())).thenReturn(aResultWithPayment(payment));

            // when & then
            mockMvc.perform(get(ENDPOINT + "/" + ORDER_NUMBER)
                            .header(HEADER_OF_MEMBER_ID, memberId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderNumber").value(ORDER_NUMBER))
                    .andExpect(jsonPath("$.data.status").value("PAID"))
                    .andExpect(jsonPath("$.data.totalAmount").value(20_000))
                    .andExpect(jsonPath("$.data.orderedAt").exists())
                    .andExpect(jsonPath("$.data.paidAt").exists())
                    .andExpect(jsonPath("$.data.lines[0].productId").value(7))
                    .andExpect(jsonPath("$.data.lines[0].productName").value("무선 이어폰"))
                    .andExpect(jsonPath("$.data.lines[0].unitPrice").value(10_000))
                    .andExpect(jsonPath("$.data.lines[0].quantity").value(2))
                    .andExpect(jsonPath("$.data.lines[0].lineAmount").value(20_000))
                    .andExpect(jsonPath("$.data.payment.method").value("POINT"))
                    .andExpect(jsonPath("$.data.payment.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.payment.approvedAt").exists())
                    .andExpect(jsonPath("$.data.id").doesNotExist())
                    .andExpect(jsonPath("$.data.lines[0].id").doesNotExist());
        }

        @Test
        @DisplayName("결제 요청 전이면 payment 는 비어 있다")
        void returnsNullPayment_whenPaymentDoesNotExist() throws Exception {
            // given
            long memberId = 1L;
            when(orderUseCase.getOrder(any())).thenReturn(aResultWithPayment(null));

            // when & then
            mockMvc.perform(get(ENDPOINT + "/" + ORDER_NUMBER)
                            .header(HEADER_OF_MEMBER_ID, memberId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.payment").isEmpty());
        }
    }
}
