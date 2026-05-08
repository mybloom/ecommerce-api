package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrandV1Controller.class)
class BrandV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandUseCase brandUseCase;

    @Nested
    @DisplayName("GET /api/v1/brands/{brandId}")
    class GetBrand {

        @Test
        @DisplayName("brandId가 숫자가 아니면 400 Bad Request를 반환한다")
        void returnsBadRequest_whenBrandIdIsNotNumeric() throws Exception {
            String brandId = "string";

            mockMvc.perform(get("/api/v1/brands/"+ brandId))
                    .andExpect(status().isBadRequest());
        }
    }
}
