package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
