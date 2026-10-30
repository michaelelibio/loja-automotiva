package com.garage.garageapi.order.quote;

import com.garage.garageapi.integration.aliexpress.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.payment.creation-enabled=false")
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AssistedFreightSuggestionsHttpTests {
    @Autowired MockMvc mvc;
    @MockitoBean AssistedQuoteService quotes;
    @MockitoBean AliExpressFreightService freight;
    @Test void adminUsesOnlyServerOrderSnapshotAndReceivesNoCommercialAuthorization() throws Exception {
        var context=new AliExpressAssistedFreightPreview.Context(7L,new BigDecimal("50"),"SC",
            List.of(new AliExpressAssistedFreightPreview.Item("123","456",2)));
        when(quotes.freightContext(7L)).thenReturn(context);
        when(freight.previewAssisted(context)).thenReturn(new AliExpressAssistedFreightPreview.Result(7L,Instant.now(),Instant.now().plusSeconds(600),
            "COUNTRY_AND_PROVINCE_ONLY","Santa Catarina",false,List.of(),
            AliExpressAssistedFreightPreview.Costs.calculate(null,null,null,null,null,null),List.of("TAXES_NOT_CONFIRMED")));
        mvc.perform(post("/api/admin/orders/7/quote/suggestions")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .contentType("application/json").content("{\"skuId\":\"999\",\"quantity\":99}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.orderId").value(7)).andExpect(jsonPath("$.commercialAuthorized").value(false))
            .andExpect(jsonPath("$.costs.status").value("INCOMPLETE"))
            .andExpect(jsonPath("$.checkoutUrl").doesNotExist());
        verify(quotes).freightContext(7L);verifyNoMoreInteractions(quotes);
        verify(freight).previewAssisted(context);verifyNoMoreInteractions(freight);
    }
    @Test void customerCannotQueryAnotherOrdersSupplierCosts() throws Exception {
        mvc.perform(post("/api/admin/orders/7/quote/suggestions")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        verifyNoInteractions(quotes,freight);
    }
    @Test void anonymousCannotQuerySupplierCosts() throws Exception {
        mvc.perform(post("/api/admin/orders/7/quote/suggestions")).andExpect(status().isUnauthorized());
        verifyNoInteractions(quotes,freight);
    }
}
