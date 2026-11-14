package com.garage.garageapi.integration.aliexpress;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Optional read-only observations. The official create response decides method authorization. */
@Component
public class AliExpressTestReadiness {
    private final AliExpressProductClient products;
    private final AliExpressProductNormalizer normalizer;
    private final AliExpressTokenService tokens;
    public AliExpressTestReadiness(AliExpressProductClient products,AliExpressProductNormalizer normalizer,AliExpressTokenService tokens){this.products=products;this.normalizer=normalizer;this.tokens=tokens;}
    public Check check(AliExpressTestFixture base,String token) {
        var warnings=new ArrayList<String>();String buyerId=null,buyer="Conta do OAuth existente — identificação não retornada pelo provedor",state="UNKNOWN";
        try {var identity=tokens.buyerIdentity();buyerId=identity.userId();buyer=identity.accountId()==null?"AliExpress user_id "+buyerId:identity.accountId()+" (user_id "+buyerId+")";state="KNOWN";}
        catch(RuntimeException ignored){warnings.add("Não foi possível comprovar o nome da conta compradora. A criação usará exatamente a conta do OAuth existente; confira essa conta em Meus pedidos.");}
        var observed=base;String source=base.productCost()==null?"UNKNOWN":"IMPORTED_VARIANT";
        try {
            var raw=products.get(base.productId(),"BR","BRL","pt",token);var product=normalizer.normalize(raw,base.productId(),"BR");
            var sku=product.variants().stream().filter(v->base.skuId().equals(v.skuId())).findFirst().orElseThrow();
            if(!base.skuAttr().equals(sku.rawSkuAttr()))throw new IllegalStateException();
            if("BRL".equals(sku.currency())) {observed=base.withProductCost(sku.price().multiply(java.math.BigDecimal.valueOf(base.quantity())));source="LIVE_PRODUCT_READ";}
            if(sku.availableStock()<base.quantity())warnings.add("A leitura de estoque indica disponibilidade insuficiente; a API decidirá se a criação é aceita.");
        } catch(RuntimeException ignored){warnings.add("A consulta de produto não confirmou o custo atual. Isso não comprova nem nega permissão de criação; a API retornará o resultado oficial.");}
        if("IMPORTED_VARIANT".equals(source))warnings.add("O custo do produto vem da importação e pode estar desatualizado.");
        if(observed.deliveryService()==null)warnings.add("Modalidade ainda desconhecida: o campo opcional será omitido e o AliExpress poderá escolher ou rejeitar a entrega.");
        if(observed.shippingCost()==null)warnings.add("Frete do fornecedor desconhecido.");
        if(observed.taxCost()==null)warnings.add("Impostos desconhecidos.");
        warnings.add("O custo pode superar o valor registrado na inGarage. Valores finais e encargos devem ser conferidos no AliExpress antes do pagamento manual.");
        Instant now=Instant.now();return new Check(observed,buyerId,buyer,state,now,now.plusSeconds(120),List.copyOf(warnings),source);
    }
    public record Check(AliExpressTestFixture fixture,String buyerUserId,String buyerAccount,String buyerState,Instant checkedAt,Instant validUntil,List<String> warnings,String productCostSource) {}
}
