package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.*;
import java.time.Instant;
import java.util.*;
import static com.garage.garageapi.integration.aliexpress.AliExpressIntegrationException.Reason.*;

@Service
public class AliExpressProductService {
    private final AliExpressProductClient client;
    private final AliExpressTokenService tokens;
    private final AliExpressProductNormalizer normalizer;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductMediaRepository media;
    public AliExpressProductService(AliExpressProductClient client, AliExpressTokenService tokens,
            AliExpressProductNormalizer normalizer, ProductRepository products,
            ProductVariantRepository variants, ProductMediaRepository media) {
        this.client = client; this.tokens = tokens; this.normalizer = normalizer;
        this.products = products; this.variants = variants; this.media = media;
    }
    public AliExpressProductPreview preview(String input, String country, String currency, String language) {
        String id = AliExpressProductInput.productId(input);
        if (country == null || !Arrays.asList(Locale.getISOCountries()).contains(country)
                || currency == null || !currency.matches("[A-Z]{3}") || language == null || !language.matches("[a-z]{2}(?:_[A-Z]{2})?")) throw new AliExpressIntegrationException(INVALID_INPUT);
        try { Currency.getInstance(currency); } catch (IllegalArgumentException ex) { throw new AliExpressIntegrationException(INVALID_INPUT); }
        return normalizer.normalize(client.get(id, country, currency, language, tokens.accessToken()), id, country);
    }
    @Transactional
    public Map<String,Object> importProduct(AliExpressAdminController.ImportRequest request) {
        if (request.salePriceBrl() == null || request.salePriceBrl().signum() <= 0 || request.salePriceBrl().scale() > 2
                || request.salePriceBrl().precision() - request.salePriceBrl().scale() > 10) throw new AliExpressIntegrationException(INVALID_INPUT);
        AliExpressProductPreview source = preview(request.input(),request.shipToCountry(),request.targetCurrency(),request.targetLanguage());
        if (!source.fingerprint().equals(request.fingerprint())) throw new AliExpressIntegrationException(STALE_PREVIEW);
        if (products.existsBySupplierIgnoreCaseAndSupplierProductId("ALIEXPRESS", source.productId())) throw new AliExpressIntegrationException(DUPLICATE);
        Map<String,Integer> overrides = request.variantStocks() == null ? Map.of() : request.variantStocks();
        Set<String> ids = new HashSet<>(); source.variants().forEach(v -> ids.add(v.skuId()));
        if (!ids.containsAll(overrides.keySet())) throw new AliExpressIntegrationException(INVALID_INPUT);
        String sku = "AE-" + source.productId();
        String slug = "aliexpress-" + source.productId();
        if (products.existsBySlug(slug) || products.existsBySkuIgnoreCase(sku)) throw new AliExpressIntegrationException(DUPLICATE);
        BigDecimal cost = source.variants().stream().map(AliExpressProductPreview.Variant::price).min(BigDecimal::compareTo).orElseThrow();
        String shortDescription = truncate(source.description(),1000);
        String mainImage = source.images().stream().filter(url -> url.length() <= 500).findFirst().orElse(null);
        Product product = new Product(truncate(source.name(),150),slug,shortDescription,truncate(source.description(),5000),
                request.salePriceBrl(),null,"Nacionais",0,mainImage,false);
        product.updateAdmin(product.getName(),slug,shortDescription,product.getLongDescription(),request.salePriceBrl(),null,
                "BRL".equals(source.currency()) ? cost.setScale(2,RoundingMode.HALF_UP) : null,
                "Nacionais",mainImage,false,ProductType.SINGLE,sku);
        product.linkSupplier("ALIEXPRESS",source.productId(),"USD".equals(source.currency()) ? cost : null,null,Instant.now());
        product.configureFulfillment(FulfillmentType.DROPSHIPPING);
        Map<String,Object> metadata = new TreeMap<>();
        metadata.put("originalName",source.name()); metadata.put("description",source.description());
        metadata.put("attributes",source.attributes()); metadata.put("packageInfo",source.packageInfo());
        metadata.put("logistics",source.logistics()); metadata.put("store",source.store());
        metadata.put("shipToCountry",source.shipToCountry()); metadata.put("images",source.images());
        metadata.put("minimumSupplierCost",cost.toPlainString()); metadata.put("domesticShippingConfirmed",false);
        Map<String,Integer> stockSnapshot = new TreeMap<>();
        Map<String,List<String>> variantImages = new TreeMap<>();
        source.variants().forEach(v -> { stockSnapshot.put(v.skuId(),v.availableStock()); variantImages.put(v.skuId(),v.images()); });
        metadata.put("stockSnapshot",stockSnapshot); metadata.put("variantImages",variantImages);
        product.configureSupplierMetadata(source.currency(),metadata);
        try {
            products.saveAndFlush(product);
            LinkedHashSet<String> allImages = new LinkedHashSet<>(source.images());
            for (var variant : source.variants()) {
                Integer reviewedStock = overrides.getOrDefault(variant.skuId(),variant.availableStock());
                if (reviewedStock == null) throw new AliExpressIntegrationException(INVALID_INPUT);
                int stock = reviewedStock;
                if (stock < 0 || stock > variant.availableStock()) throw new AliExpressIntegrationException(INVALID_INPUT);
                if (variants.findBySupplierIgnoreCaseAndSupplierVariantId("ALIEXPRESS",variant.skuId()).isPresent()) throw new AliExpressIntegrationException(DUPLICATE);
                ProductVariant saved = new ProductVariant(product,"ALIEXPRESS",variant.skuId(),source.productId(),variant.skuCode(),
                        variant.skuId(),variant.attributes(),variant.rawSkuAttr(),variant.price(),variant.currency(),
                        variant.images().stream().filter(url -> url.length() <= 500).findFirst().orElse(null),null,null,null,null);
                saved.setSupplierStockQuantity(stock);
                variants.save(saved); allImages.addAll(variant.images());
            }
            int position = 0;
            for (String image : allImages) {
                // Curated import uses existing MANUAL gallery source; supplier is ALIEXPRESS on product/variants.
                media.save(new ProductMedia(product,ProductMediaType.IMAGE,image,image,position++,truncate(source.name(),500),ProductMediaSource.MANUAL));
            }
            variants.flush(); media.flush();
        } catch (DataIntegrityViolationException ignored) { throw new AliExpressIntegrationException(DUPLICATE); }
        return Map.of("productId",product.getId(),"supplierProductId",source.productId(),"active",false,"variantCount",source.variants().size());
    }
    @Transactional
    public Map<String,Object> reviewStock(Long productId, Long variantId, int quantity) {
        Product product = products.findByIdWithLock(productId).orElseThrow(() -> new AliExpressIntegrationException(NOT_FOUND));
        if (!"ALIEXPRESS".equals(product.getSupplier()) || Boolean.TRUE.equals(product.getActive()) || quantity < 0) throw new AliExpressIntegrationException(INVALID_INPUT);
        ProductVariant variant = variants.findById(variantId).orElseThrow(() -> new AliExpressIntegrationException(NOT_FOUND));
        if (!variant.getProduct().getId().equals(productId) || !"ALIEXPRESS".equals(variant.getSupplier())) throw new AliExpressIntegrationException(INVALID_INPUT);
        Object snapshot = product.getSupplierMetadata() == null ? null : product.getSupplierMetadata().get("stockSnapshot");
        if (!(snapshot instanceof Map<?,?> stock) || !(stock.get(variant.getSupplierVariantId()) instanceof Number maximum)
                || quantity > maximum.intValue()) throw new AliExpressIntegrationException(INVALID_INPUT);
        variant.setSupplierStockQuantity(quantity);
        return Map.of("variantId",variantId,"supplierStockQuantity",quantity,"active",false);
    }
    @Transactional(readOnly = true)
    public List<Map<String,Object>> stock(Long productId) {
        Product product = products.findById(productId).orElseThrow(() -> new AliExpressIntegrationException(NOT_FOUND));
        if (!"ALIEXPRESS".equals(product.getSupplier())) throw new AliExpressIntegrationException(INVALID_INPUT);
        return variants.findAllByProductIdOrderByIdAsc(productId).stream().map(v -> Map.<String,Object>of(
                "variantId",v.getId(),"supplierVariantId",v.getSupplierVariantId(),
                "supplierStockQuantity",v.getSupplierStockQuantity())).toList();
    }
    private static String truncate(String value,int max) { return value == null ? null : value.substring(0,Math.min(value.length(),max)); }
}
