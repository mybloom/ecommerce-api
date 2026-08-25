package com.loopers.interfaces.api.productlike;

import com.loopers.application.productlike.ProductLikeUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductLikeV1Controller.class)
class ProductLikeV1ControllerTest {

    private static final String HEADER_OF_MEMBER_ID = "X-MEMBER-ID";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductLikeUseCase productLikeUseCase;

    @Nested
    @DisplayName("POST /api/v1/like/products/{productId}")
    class Like {

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsMissing() throws Exception {
            long productId = 1L;

            mockMvc.perform(post("/api/v1/like/products/" + productId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("productId가 숫자가 아니면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenProductIdIsNotNumeric() throws Exception {
            String invalidProductId = "string";
            long memberId = 1L;

            mockMvc.perform(post("/api/v1/like/products/" + invalidProductId)
                            .header(HEADER_OF_MEMBER_ID, memberId))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/like/products/{productId}")
    class Unlike {

        @Test
        @DisplayName("X-MEMBER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsMissing() throws Exception {
            long productId = 1L;

            mockMvc.perform(delete("/api/v1/like/products/" + productId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("productId가 숫자가 아니면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenProductIdIsNotNumeric() throws Exception {
            String invalidProductId = "string";
            long memberId = 1L;

            mockMvc.perform(delete("/api/v1/like/products/" + invalidProductId)
                            .header(HEADER_OF_MEMBER_ID, memberId))
                    .andExpect(status().isBadRequest());
        }
    }
}
