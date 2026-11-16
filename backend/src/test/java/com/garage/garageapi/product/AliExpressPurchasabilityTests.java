package com.garage.garageapi.product;

import com.garage.garageapi.product.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AliExpressPurchasabilityTests {
    @Test void originalSkuAttributesMustAgreeWithProviderPropertyIdentifiersWithoutDerivingNewIds() {
        var product=new Product("Produto","sku-check",null,null,BigDecimal.TEN,null,"Acessórios",0,null,true);
        var valid=new ProductVariant(product,"ALIEXPRESS","201","1005000000000",null,"Preto",Map.of("14","Preto","14:valueId","771"),"14:771#Black",BigDecimal.ONE,"BRL",null,null,null,null,null);
        var invalid=new ProductVariant(product,"ALIEXPRESS","202","1005000000000",null,"Vermelho",Map.of("14","Vermelho","14:valueId","999"),"14:772#Red",BigDecimal.ONE,"BRL",null,null,null,null,null);
        assertThat(AliExpressPurchasability.hasSelection(valid)).isTrue();
        assertThat(AliExpressPurchasability.hasSelection(invalid)).isFalse();
        assertThat(invalid.getRawVariantKey()).isEqualTo("14:772#Red");
    }
}
