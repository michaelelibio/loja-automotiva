package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliExpressFreightWiringTests {
    @Test void adminFacadeAndConcreteAdapterShareOneSpringClientInstance() {
        var shared=mock(AliExpressDsFreightClient.class);
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(AliExpressDsFreightClient.class,()->shared);
            context.registerBean(com.garage.garageapi.product.repository.ProductRepository.class,
                    ()->mock(com.garage.garageapi.product.repository.ProductRepository.class));
            context.registerBean(com.garage.garageapi.product.repository.ProductVariantRepository.class,
                    ()->mock(com.garage.garageapi.product.repository.ProductVariantRepository.class));
            context.registerBean(AliExpressTokenService.class,()->mock(AliExpressTokenService.class));
            context.register(AliExpressFreightDiagnosticClient.class,AliExpressDsFreightGateway.class,AliExpressFreightService.class);
            context.refresh();
            assertThat(context.getBean(AliExpressFreightGateway.class)).isInstanceOf(AliExpressDsFreightGateway.class);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(AliExpressFreightDiagnosticClient.class),"client")).isSameAs(shared);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(AliExpressDsFreightGateway.class),"client")).isSameAs(shared);
        }
    }
    @Test void springInjectsConfiguredGatewayAndPublicQuoteActuallyCallsIt() {
        var gateway=mock(AliExpressFreightGateway.class);
        var request=new ShippingProvider.Request("89229030",List.of());
        when(gateway.calculate(request)).thenThrow(new AliExpressShippingException(AliExpressShippingException.Code.PERMISSION_DENIED));
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(AliExpressFreightGateway.class,()->gateway);
            context.register(AliExpressFreightService.class);
            context.refresh();
            var service=context.getBean(AliExpressFreightService.class);
            assertThatThrownBy(()->service.quote(request)).isInstanceOfSatisfying(AliExpressShippingException.class,
                    ex->assertThat(ex.code()).isEqualTo(AliExpressShippingException.Code.PERMISSION_DENIED));
            verify(gateway).calculate(request);
        }
    }
    @Test void successfulCountryRatesDoNotAuthorizeCheckout() {
        var gateway=mock(AliExpressFreightGateway.class);
        var request=new ShippingProvider.Request("89229030",List.of());
        var method=new AliExpressFreightDiagnosticResult.Method("STANDARD",new java.math.BigDecimal("12.30"),"BRL","5-8days","Standard","CN");
        when(gateway.calculate(request)).thenReturn(new AliExpressFreightGateway.Evaluation(AliExpressFreightGateway.Coverage.COUNTRY_ONLY,
                List.of(new AliExpressFreightGateway.LineQuote(1L,2L,1,List.of(method)))));
        assertThatThrownBy(()->new AliExpressFreightService(gateway).quote(request)).isInstanceOfSatisfying(AliExpressShippingException.class,
                ex->assertThat(ex.code()).isEqualTo(AliExpressShippingException.Code.DESTINATION_NOT_CONFIRMED));
        verify(gateway).calculate(request);
    }
}
