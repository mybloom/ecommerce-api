package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductV1Controller.class)
class ProductV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductUseCase productUseCase;

    @Nested
    @DisplayName("GET /api/v1/products/{productId}")
    class GetProduct {

        @Test
        @DisplayName("productId가 숫자가 아니면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenProductIdIsNotNumeric() throws Exception {
            String invalidProductId = "string";

            mockMvc.perform(get("/api/v1/products/" + invalidProductId))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class GetProducts {
        private static final String ENDPOINT = "/api/v1/products";

        @Test
        @DisplayName("page가 숫자가 아니면 400과 함께 실패 형식(meta.result=FAIL, errorCode)을 응답한다")
        void returnsBadRequestWithFailureBody_whenPageIsNotNumeric() throws Exception {
            // given
            String invalidPage = "first";

            // when & then
            mockMvc.perform(get(ENDPOINT).param("page", invalidPage))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.meta.result").value(ApiResponse.Metadata.Result.FAIL.name()))
                    .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.BAD_REQUEST.getCode()))
                    .andExpect(jsonPath("$.meta.message").exists());
        }

        @Test
        @DisplayName("size가 숫자가 아니면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenSizeIsNotNumeric() throws Exception {
            // given
            String invalidSize = "many";

            // when & then
            mockMvc.perform(get(ENDPOINT).param("size", invalidSize))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-MEMBER-ID가 숫자가 아니면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenMemberIdHeaderIsNotNumeric() throws Exception {
            // given
            String invalidMemberId = "guest";

            // when & then
            mockMvc.perform(get(ENDPOINT).header("X-MEMBER-ID", invalidMemberId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("brandId가 숫자가 아니면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenBrandIdIsNotNumeric() throws Exception {
            // given
            String invalidBrandId = "nike";

            // when & then
            mockMvc.perform(get(ENDPOINT).param("brandId", invalidBrandId))
                    .andExpect(status().isBadRequest());
        }
    }
}
