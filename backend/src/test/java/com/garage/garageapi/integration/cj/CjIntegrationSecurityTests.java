package com.garage.garageapi.integration.cj;

import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.service.CjProductService;
import com.garage.garageapi.integration.cj.service.CjProductImportService;
import com.garage.garageapi.integration.cj.dto.CjProductImportResponse;
import com.garage.garageapi.integration.cj.dto.CjProductVariantsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CjIntegrationSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean CjProductService productService;
    @MockitoBean CjProductImportService importService;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/integrations/cj/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/integrations/cj/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReceivesSafeProductDto() throws Exception {
        when(productService.list(null, 1, 10)).thenReturn(new CjProductResponse(1, 10, 0, 0,
                List.of()));

        mockMvc.perform(get("/api/admin/integrations/cj/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("accessToken"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("refreshToken"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("apiKey"))));
    }

    @Test
    void regularUserCannotImportProduct() throws Exception {
        mockMvc.perform(post("/api/admin/integrations/cj/products/cj-1/import")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedVariantRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/integrations/cj/products/PID-1/variants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotQueryVariants() throws Exception {
        mockMvc.perform(get("/api/admin/integrations/cj/products/PID-1/variants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanQueryVariantsWithoutPersistence() throws Exception {
        when(productService.getVariants("PID-1"))
                .thenReturn(new CjProductVariantsResponse("PID-1", java.util.List.of()));

        mockMvc.perform(get("/api/admin/integrations/cj/products/PID-1/variants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"productId\":\"PID-1\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"variants\":[]")));
    }

    @Test
    void adminCanImportProduct() throws Exception {
        when(importService.importProduct("cj-1")).thenReturn(new CjProductImportResponse(
                10L, "Produto CJ", "produto-cj", "CJ-1", "https://example.test/image.jpg",
                "CJ", "cj-1", new java.math.BigDecimal("1.90"),
                new java.math.BigDecimal("5.50"), java.time.Instant.now(),
                new java.math.BigDecimal("10.45"), new java.math.BigDecimal("10.45"),
                "NÃO CATEGORIZADO", 0, false));

        mockMvc.perform(post("/api/admin/integrations/cj/products/cj-1/import")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"supplier\":\"CJ\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("accessToken"))));
    }
}
