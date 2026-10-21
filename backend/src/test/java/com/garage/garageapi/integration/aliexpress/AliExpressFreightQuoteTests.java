package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressFreightQuote.*;
import static org.assertj.core.api.Assertions.*;

class AliExpressFreightQuoteTests {
    final Instant now = Instant.parse("2026-10-15T12:00:00Z");
    Context context(String zip, int quantity) {
        return new Context(List.of(new Line("1005010661577657", "12000053109276547", quantity, "CN")),
                new Destination("BR", zip, "SC", "Joinville", "Rua Teste", "10", "Centro", null));
    }
    Method method(String service, String amount) { return new Method(service, new BigDecimal(amount), "BRL", 8, 15); }
    AliExpressFreightQuote quote(String amount) {
        return new AliExpressFreightQuote(context("89229030", 1), List.of(method("SIMULATED_STANDARD", amount)), now);
    }

    @Test void simulatedGatewayPreservesMultipleMethodsAndExactAmounts() {
        Context context = context("89229030", 1);
        var quote = new AliExpressFreightQuote(context,List.of(method("SIMULATED_STANDARD", "12.30"),method("SIMULATED_EXPRESS", "25.39")),now);
        assertThat(quote.methods()).hasSize(2);
        assertThat(quote.select(context, "SIMULATED_EXPRESS", now).amount()).isEqualTo(new BigDecimal("25.39"));
        assertThat(quote.methods().get(0).currency()).isEqualTo("BRL");
        assertThat(quote.methods().get(0).estimatedMaxDays()).isEqualTo(15);
        assertThatThrownBy(() -> quote.methods().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void changedDestinationOrQuantityInvalidatesSelection() {
        var quote = quote("12.30");
        for (Context changed : List.of(context("01001000", 1), context("89229030", 2)))
            assertThatThrownBy(() -> quote.select(changed, "SIMULATED_STANDARD", now)).isInstanceOf(ResourceConflictException.class);
        var old = context("89229030", 1);
        var changedStreet = new Context(old.items(), new Destination("BR", "89229030", "SC", "Joinville", "Outra Rua", "20", "Centro", null));
        assertThatThrownBy(() -> quote.select(changedStreet, "SIMULATED_STANDARD", now)).isInstanceOf(ResourceConflictException.class);
    }

    @Test void expiredOrFutureQuoteCannotBeUsed() {
        var quote = quote("12.30");
        for (Instant time : List.of(now.minusSeconds(1), now.plus(VALIDITY), now.plus(VALIDITY).plusSeconds(1)))
            assertThatThrownBy(() -> quote.select(quote.context(), "SIMULATED_STANDARD", time)).isInstanceOf(ResourceConflictException.class);
    }
    @Test void skuCartOriginAndAddressFieldsInvalidateInternalCandidateEvenWithSameZip() {
        var quote=quote("12.30");
        var current=quote.context();
        var original=current.items().get(0);
        for(var changed:List.of(
                new Context(List.of(new Line(original.productId(),"12000053109276548",1,"CN")),current.destination()),
                new Context(List.of(original,new Line("1005010661577658","12000053109276548",1,"CN")),current.destination()),
                new Context(List.of(new Line(original.productId(),original.variantId(),1,"BR")),current.destination()),
                new Context(current.items(),new Destination("BR","89229030","SC","Outra Cidade","Rua Teste","10","Centro",null)),
                new Context(current.items(),new Destination("BR","89229030","SP","Joinville","Rua Teste","10","Centro",null)),
                new Context(current.items(),new Destination("BR","89229030","SC","Joinville","Rua Teste","10","Centro","Apto 2")))) {
            assertThatThrownBy(()->quote.select(changed,"SIMULATED_STANDARD",now))
                    .isInstanceOf(ResourceConflictException.class);
        }
    }

    @Test void missingRatesOrUnknownMethodNeverMeanFreeShipping() {
        assertThatThrownBy(() -> new AliExpressFreightQuote(context("89229030", 1), List.of(), now)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> quote("12.30").select(context("89229030", 1), "FORGED", now)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> new Method("SIMULATED", null, "BRL", null, null)).isInstanceOf(ResourceConflictException.class);
        assertThat(quote("0.00").methods().get(0).amount()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test void rejectsInvalidMoneyAndUnconfirmedCurrencyWithoutConversionOrRounding() {
        for (String amount : List.of("-1", "0.001", "10000000000"))
            assertThatThrownBy(() -> method("SIMULATED", amount)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> new Method("SIMULATED", BigDecimal.TEN, "USD", 8, 15)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> new Method("SIMULATED", BigDecimal.TEN, "BRL", 15, 8)).isInstanceOf(ResourceConflictException.class);
    }

    @Test void changedFreightRequiresReviewAndPreservesPaidAmount() {
        var paid = quote("12.30");
        assertThatThrownBy(() -> paid.requireUnchanged("SIMULATED_STANDARD", quote("13.30"), now))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("revisão administrativa");
        assertThatCode(() -> paid.requireUnchanged("SIMULATED_STANDARD", quote("12.30"), now)).doesNotThrowAnyException();
        assertThat(paid.methods().get(0).amount()).isEqualTo(new BigDecimal("12.30"));
    }

    @Test void duplicateMethodsAndUntrustedOriginAreRejected() {
        assertThatThrownBy(() -> new AliExpressFreightQuote(context("89229030", 1), List.of(method("SIMULATED", "10"), method("SIMULATED", "20")), now))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> new Line("1005010661577657", "12000053109276547", 1, null)).isInstanceOf(ResourceConflictException.class);
        assertThat(quote("12.30").toString()).doesNotContain("89229030", "Rua Teste");
        assertThat(context("89229030", 1).toString()).doesNotContain("89229030", "Rua Teste");
    }

    @Test void communicationFailureNeverProducesAnEmptyOrZeroQuote() {
        AliExpressFreightGateway gateway = context -> { throw new IllegalStateException("token=secret destination=89229030"); };
        var service = new AliExpressFreightService(gateway);
        assertThatThrownBy(() -> service.quote(new com.garage.garageapi.shipping.provider.ShippingProvider.Request("89229030",List.of()))).isInstanceOf(ResourceConflictException.class)
                .hasMessage("Nao foi possivel concluir a consulta de frete AliExpress.").hasNoCause();
    }
}
