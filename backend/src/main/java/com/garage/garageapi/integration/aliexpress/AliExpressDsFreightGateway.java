package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressShippingException.Code.*;

/** Real read-only adapter. Country-scoped observations are never commercial checkout quotes. */
@Component
public class AliExpressDsFreightGateway implements AliExpressFreightGateway {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final AliExpressTokenService tokens;
    private final AliExpressDsFreightClient client;
    public AliExpressDsFreightGateway(ProductRepository products,ProductVariantRepository variants,
            AliExpressTokenService tokens,AliExpressDsFreightClient client) {
        this.products=products;this.variants=variants;this.tokens=tokens;this.client=client;
    }
    private record Key(Long productId,Long variantId) {}
    private record Trusted(Key key,String productId,String sku,int quantity) {}
    @Override public Evaluation calculate(ShippingProvider.Request request) {
        if (request == null || request.zipCode() == null || !request.zipCode().matches("[0-9]{5}-?[0-9]{3}")
                || request.items() == null || request.items().isEmpty() || request.items().size()>100)
            throw new AliExpressShippingException(INVALID_PARAMETERS);
        Map<Key,Integer> quantities=new LinkedHashMap<>();
        for (var item:request.items()) {
            if (item == null || item.productId() == null || item.quantity()<1)
                throw new AliExpressShippingException(INVALID_PARAMETERS);
            try { quantities.merge(new Key(item.productId(),item.productVariantId()),item.quantity(),Math::addExact); }
            catch (ArithmeticException ignored) { throw new AliExpressShippingException(INVALID_PARAMETERS); }
        }
        List<Trusted> lines=new ArrayList<>();
        for (var entry:quantities.entrySet()) {
            var key=entry.getKey();int quantity=entry.getValue();
            var product=products.findById(key.productId()).orElseThrow(()->new AliExpressShippingException(INVALID_PARAMETERS));
            // Supplier/type/IDs from a ShippingProvider.Item are not authority: re-read the DB.
            if (!"ALIEXPRESS".equalsIgnoreCase(product.getSupplier()) || product.getFulfillmentType()!=FulfillmentType.DROPSHIPPING) continue;
            if (key.variantId() == null || quantity>100 || !Boolean.TRUE.equals(product.getActive()) || !product.canFulfill(quantity))
                throw new AliExpressShippingException(INVALID_PARAMETERS);
            var variant=variants.findById(key.variantId()).orElseThrow(()->new AliExpressShippingException(INVALID_PARAMETERS));
            String productId=product.getSupplierProductId(),sku=variant.getSupplierVariantId();
            if (!Objects.equals(variant.getProduct().getId(),key.productId()) || !Boolean.TRUE.equals(variant.getActive())
                    || !"ALIEXPRESS".equalsIgnoreCase(variant.getSupplier()) || !Objects.equals(productId,variant.getSupplierProductId())
                    || productId == null || !productId.matches("[0-9]{1,18}") || sku == null || !sku.matches("[0-9]{1,30}"))
                throw new AliExpressShippingException(INVALID_PARAMETERS);
            if (variant.getSupplierStockQuantity() == null || variant.getSupplierStockQuantity()<quantity)
                throw new AliExpressShippingException(FREIGHT_UNAVAILABLE);
            lines.add(new Trusted(key,productId,sku,quantity));
        }
        if (lines.isEmpty()) throw new AliExpressShippingException(INVALID_PARAMETERS);
        final String token;
        try { token=tokens.existingAccessToken(); }
        catch (RuntimeException ignored) { throw new AliExpressShippingException(AUTHENTICATION_FAILED); }
        if (token == null || token.isBlank()) throw new AliExpressShippingException(AUTHENTICATION_FAILED);
        List<LineQuote> quotes=new ArrayList<>();
        for (var line:lines) {
            // No CEP, street, UF/IBGE guesses, origin inference, or fallback currency/provider.
            var result=client.query(token,new AliExpressDsFreightClient.Input(line.productId(),line.sku(),line.quantity(),"BR"));
            if (result == null) throw new AliExpressShippingException(INVALID_RESPONSE);
            if (result.status()!=AliExpressFreightDiagnosticResult.Status.SUCCESS) throw rejected(result);
            if (result.methods().isEmpty()) throw new AliExpressShippingException(FREIGHT_UNAVAILABLE);
            quotes.add(new LineQuote(line.key().productId(),line.key().variantId(),line.quantity(),result.methods()));
        }
        return new Evaluation(Coverage.COUNTRY_ONLY,quotes);
    }
    private static AliExpressShippingException rejected(AliExpressFreightDiagnosticResult result) {
        if ("AMBIGUOUS_MONETARY_UNIT".equals(result.safeCode()) || "CONFLICTING_MONETARY_FIELDS".equals(result.safeCode()))
            return new AliExpressShippingException(AMBIGUOUS_MONEY);
        if ("INVALID_DELIVERY_METHOD".equals(result.safeCode())) return new AliExpressShippingException(INVALID_DELIVERY_METHOD);
        return new AliExpressShippingException(switch(result.status()) {
            case PERMISSION_DENIED -> PERMISSION_DENIED;
            case TOKEN_REJECTED, LOCAL_AUTHORIZATION_UNAVAILABLE -> AUTHENTICATION_FAILED;
            case PARAMETER_ERROR -> INVALID_PARAMETERS;
            case PRODUCT_UNAVAILABLE -> FREIGHT_UNAVAILABLE;
            case NOT_CONFIGURED -> NOT_CONFIGURED;
            case RATE_LIMITED -> RATE_LIMITED;
            case INVALID_RESPONSE -> INVALID_RESPONSE;
            default -> EXTERNAL_FAILURE;
        });
    }
}
