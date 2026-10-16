package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AliExpressProductImportIntegrationTests {
    private static final String BASE="/api/admin/integrations/aliexpress";
    private static final String ID="1005008752784588";
    private static final String PREVIEW="{\"input\":\"https://pt.aliexpress.com/item/1005008752784588.html?spm=test\",\"shipToCountry\":\"US\",\"targetCurrency\":\"USD\",\"targetLanguage\":\"en\"}";
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @MockitoSpyBean ProductMediaRepository media;
    @Autowired AliExpressProductService service;
    @MockitoBean AliExpressProductClient client;
    @MockitoBean AliExpressTokenService tokens;
    @BeforeEach void clean() throws Exception {
        reset(media);media.deleteAll();variants.deleteAll();products.deleteAll();reset(client,tokens);
        when(tokens.accessToken()).thenReturn("synthetic-provider-token");
        when(client.get(ID,"US","USD","en","synthetic-provider-token")).thenAnswer(invocation->AliExpressProductNormalizerTests.source());
    }
    @Test void previewIsReadOnlyAndContainsNoTokens() throws Exception {
        mvc.perform(post(BASE+"/preview").with(admin()).contentType(MediaType.APPLICATION_JSON).content(PREVIEW))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productId").value(ID))
                .andExpect(jsonPath("$.variants.length()").value(4)).andExpect(jsonPath("$.images.length()").value(6))
                .andExpect(jsonPath("$.currency").value("USD")).andExpect(jsonPath("$.domesticShippingConfirmed").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-provider-token"))));
        assertThat(products.count()).isZero();assertThat(variants.count()).isZero();assertThat(media.count()).isZero();
    }
    @Test void confirmedBrazilPreviewPreservesAttributesPricesAndStockWithoutPersistence() throws Exception {
        when(client.get("1005010661577657","BR","BRL","pt_BR","synthetic-provider-token"))
                .thenReturn(AliExpressProductNormalizerTests.confirmedResponse().path("aliexpress_ds_product_get_response").path("result"));
        mvc.perform(post(BASE+"/preview").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"input":"1005010661577657","shipToCountry":"BR","targetCurrency":"BRL","targetLanguage":"pt_BR"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productId").value("1005010661577657"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.variants[0].skuId").value("12000053109276547"))
                .andExpect(jsonPath("$.variants[0].price").value(25.39))
                .andExpect(jsonPath("$.variants[0].regularPrice").value(50.78))
                .andExpect(jsonPath("$.variants[0].availableStock").value(98))
                .andExpect(jsonPath("$.attributes['name:Head Style']").value("Hexagonal"))
                .andExpect(jsonPath("$.attributes['name:Origin']").value("Guangdong, China"))
                .andExpect(jsonPath("$.attributes['source:4:attr_name_id']").value("-1"))
                .andExpect(jsonPath("$.logistics.ship_to_country").value("Br"));
        verify(client).get("1005010661577657","BR","BRL","pt_BR","synthetic-provider-token");
        assertThat(products.count()).isZero(); assertThat(variants.count()).isZero(); assertThat(media.count()).isZero();
    }
    @Test void incompletePreviewKeepsPublicErrorContractAndDoesNotPersist() throws Exception {
        var malformed=AliExpressProductNormalizerTests.source();
        ((tools.jackson.databind.node.ObjectNode)malformed.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o").get(0)).remove("sku_id");
        when(client.get(ID,"US","USD","en","synthetic-provider-token")).thenReturn(malformed);
        mvc.perform(post(BASE+"/preview").with(admin()).contentType(MediaType.APPLICATION_JSON).content(PREVIEW))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Resposta AliExpress incompleta ou invalida; importacao cancelada."))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.stage").doesNotExist())
                .andExpect(jsonPath("$.condition").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sku_id"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-provider-token"))));
        assertThat(products.count()).isZero();assertThat(variants.count()).isZero();assertThat(media.count()).isZero();
    }
    @Test void persistsInactiveProductVariantsImagesAndOriginalCurrencyInOneTransaction() throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(importBody(preview.fingerprint(),Map.of("201",3))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.variantCount").value(4));
        assertThat(products.count()).isEqualTo(1);
        var product=products.findAll().get(0);
        assertThat(product.getSupplier()).isEqualTo("ALIEXPRESS");assertThat(product.getSupplierProductId()).isEqualTo(ID);
        assertThat(product.getActive()).isFalse();assertThat(product.getCategory()).isEqualTo("Acessórios");
        assertThat(product.getPrice()).isEqualByComparingTo("99.90");assertThat(product.getCostPrice()).isNull();
        assertThat(product.getSupplierExchangeRate()).isNull();assertThat(product.getSupplierCostCurrency()).isEqualTo("USD");
        assertThat(product.getSupplierCostUsd()).isEqualByComparingTo("8.90");
        assertThat(product.getFulfillmentType()).isEqualTo(FulfillmentType.DROPSHIPPING);assertThat(product.getStockQuantity()).isZero();
        assertThat(product.getLongDescription()).isEqualTo("Test description");
        assertThat(product.getSupplierMetadata()).containsKey("packageInfo").containsKey("stockSnapshot");
        assertThat(variants.findAllByProductIdOrderByIdAsc(product.getId())).hasSize(4);
        var first=variants.findBySupplierIgnoreCaseAndSupplierVariantId("ALIEXPRESS","201").orElseThrow();
        assertThat(first.getSupplierStockQuantity()).isEqualTo(3);assertThat(first.getSupplierCost()).isEqualByComparingTo("8.90");
        assertThat(first.getRawVariantKey()).isEqualTo("14:771#Black");assertThat(first.getWeightGrams()).isNull();
        assertThat(media.count()).isEqualTo(7);
        verify(client,times(2)).get(ID,"US","USD","en","synthetic-provider-token");
    }
    @Test void duplicateImportDoesNotOverwriteManualPrice() {
        var preview=service.preview(ID,"US","USD","en");
        var request=request(preview.fingerprint(),Map.of());service.importProduct(request);
        var product=products.findAll().get(0);
        product.updateAdmin(product.getName(),product.getSlug(),product.getDescription(),product.getLongDescription(),new BigDecimal("149.90"),null,null,
                product.getCategory(),product.getImageUrl(),false,product.getProductType(),product.getSku());products.saveAndFlush(product);
        assertThatThrownBy(()->service.importProduct(request)).isInstanceOfSatisfying(AliExpressIntegrationException.class,
                e->assertThat(e.getReason()).isEqualTo(AliExpressIntegrationException.Reason.DUPLICATE));
        assertThat(products.count()).isEqualTo(1);assertThat(products.findAll().get(0).getPrice()).isEqualByComparingTo("149.90");
    }
    @Test void selectedVariantsKeepCommercialPriceAndSupplierDataAndIgnoreActiveFromBrowser() throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        var body=(tools.jackson.databind.node.ObjectNode)AliExpressProductNormalizerTests.JSON.valueToTree(request(preview.fingerprint(),Map.of()));
        body.put("name","Nome revisado");body.put("category","Lavagem");body.put("active",true);
        body.set("variantSalePricesBrl",AliExpressProductNormalizerTests.JSON.readTree("{\"202\":79.90}"));
        body.put("costPrice",0.01);body.put("stock",999999);
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.variantCount").value(1));
        var product=products.findAll().get(0);
        assertThat(product.getName()).isEqualTo("Nome revisado");assertThat(product.getCategory()).isEqualTo("Lavagem");
        assertThat(product.getPrice()).isEqualByComparingTo("79.90");assertThat(product.getActive()).isFalse();
        var saved=variants.findAllByProductIdOrderByIdAsc(product.getId());
        assertThat(saved).hasSize(1);assertThat(saved.get(0).getSupplierVariantId()).isEqualTo("202");
        assertThat(saved.get(0).getSupplierStockQuantity()).isEqualTo(20);
        assertThat(saved.get(0).getSupplierCost()).isEqualByComparingTo("12.50");
        assertThat(product.getSupplierMetadata()).containsKey("variantSalePricesBrl");
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","{\"201\":null}","{\"201\":0}","{\"201\":-1}","{\"201\":1.234}","{\"201\":10000000000}","{\"201\":10,\"202\":20}","{\"unknown\":10}"})
    void rejectsMissingInvalidOrDifferentVariantPrices(String prices) throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        var body=(tools.jackson.databind.node.ObjectNode)AliExpressProductNormalizerTests.JSON.valueToTree(request(preview.fingerprint(),Map.of()));
        body.set("variantSalePricesBrl",AliExpressProductNormalizerTests.JSON.readTree(prices));
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());
        assertThat(products.count()).isZero();assertThat(variants.count()).isZero();assertThat(media.count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"name","category","fingerprint","variantSalePricesBrl"})
    void requiresCommercialFieldsOnServer(String field) throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        var body=(tools.jackson.databind.node.ObjectNode)AliExpressProductNormalizerTests.JSON.valueToTree(request(preview.fingerprint(),Map.of()));
        body.remove(field);
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());
        assertThat(products.count()).isZero();
    }
    @Test void rejectsInvalidCategoryOnServer() throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        var body=(tools.jackson.databind.node.ObjectNode)AliExpressProductNormalizerTests.JSON.valueToTree(request(preview.fingerprint(),Map.of()));body.put("category","Inventada");
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());
        assertThat(products.count()).isZero();
    }
    @Test void duplicateImportReturnsSafeCodeAndDoesNotCallSupplierAgain() throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        var body=importBody(preview.fingerprint(),Map.of());
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        clearInvocations(client);
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fields.code").value("DUPLICATE"));
        verifyNoInteractions(client);assertThat(products.count()).isEqualTo(1);
    }
    @Test void supplierFailureDuringImportDoesNotPersistAnything() throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        when(client.get(anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new AliExpressIntegrationException(AliExpressIntegrationException.Reason.TIMEOUT));
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(importBody(preview.fingerprint(),Map.of()))).andExpect(status().isGatewayTimeout());
        assertThat(products.count()).isZero();assertThat(variants.count()).isZero();assertThat(media.count()).isZero();
    }
    @Test void persistenceFailureRollsBackAlreadySavedProductAndVariants() throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic-internal-diagnostic"))
                .when(media).save(any(ProductMedia.class));
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(importBody(preview.fingerprint(),Map.of())))
                .andExpect(status().isBadGateway()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-internal-diagnostic"))));
        assertThat(products.count()).isZero();assertThat(variants.count()).isZero();assertThat(media.count()).isZero();
    }
    @Test void importedDraftIsListedInAdminButInvisibleToPublicUntilAdminActivatesIt() throws Exception {
        var preview=service.preview(ID,"US","USD","en");service.importProduct(request(preview.fingerprint(),Map.of()));
        var product=products.findAll().get(0);
        mvc.perform(get("/api/admin/products").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(product.getId()))
                .andExpect(jsonPath("$.content[0].active").value(false));
        for (String path:new String[]{"/api/products","/api/products?search=Produto","/api/products?category=Acessórios"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        }
        mvc.perform(get("/api/products/"+product.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/slug/"+product.getSlug())).andExpect(status().isNotFound());
        String activePath="/api/admin/products/"+product.getId()+"/active";
        mvc.perform(patch(activePath).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(patch(activePath).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")).andExpect(status().isForbidden());
        assertThat(products.findById(product.getId()).orElseThrow().getActive()).isFalse();
        mvc.perform(patch(activePath).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/products/"+product.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(99.90));
        mvc.perform(get("/api/products/slug/"+product.getSlug())).andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.content.length()").value(1));
    }
    @Test void invalidStockRollsBackProductAlreadyInserted() {
        var preview=service.preview(ID,"US","USD","en");
        assertThatThrownBy(()->service.importProduct(request(preview.fingerprint(),Map.of("204",99)))).isInstanceOf(AliExpressIntegrationException.class);
        assertThat(products.count()).isZero();assertThat(variants.count()).isZero();assertThat(media.count()).isZero();
    }
    @Test void changedPriceOrStockRequiresNewReview() throws Exception {
        var preview=service.preview(ID,"US","USD","en");
        var modified=AliExpressProductNormalizerTests.source();
        ((tools.jackson.databind.node.ObjectNode)modified.path("ae_item_sku_info_dtos").path("ae_item_sku_info_d_t_o").get(0)).put("offer_sale_price","9.99");
        when(client.get(ID,"US","USD","en","synthetic-provider-token")).thenReturn(modified);
        mvc.perform(post(BASE+"/import").with(admin()).contentType(MediaType.APPLICATION_JSON).content(importBody(preview.fingerprint(),Map.of())))
                .andExpect(status().isConflict());assertThat(products.count()).isZero();
    }
    @Test void onlyAdministratorCanPreviewImportOrStartAuthorization() throws Exception {
        for(String path:new String[]{"/preview","/import","/authorize"}) {
            mvc.perform(post(BASE+path).contentType(MediaType.APPLICATION_JSON).content(PREVIEW)).andExpect(status().isUnauthorized());
            mvc.perform(post(BASE+path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                    .contentType(MediaType.APPLICATION_JSON).content(PREVIEW)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(client,tokens);
    }
    @Test void missingOrInvalidInputDoesNotCallApi() throws Exception {
        mvc.perform(post(BASE+"/preview").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE+"/preview").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(PREVIEW.replace("https://pt.aliexpress.com/item/1005008752784588.html?spm=test","https://169.254.169.254/latest/meta-data")))
                .andExpect(status().isBadRequest());verifyNoInteractions(client,tokens);
    }
    @Test void countryRejectionIsNotSilentlyChangedToUs() throws Exception {
        when(client.get(ID,"BR","USD","en","synthetic-provider-token")).thenThrow(new AliExpressIntegrationException(AliExpressIntegrationException.Reason.COUNTRY_REJECTED));
        mvc.perform(post(BASE+"/preview").with(admin()).contentType(MediaType.APPLICATION_JSON).content(PREVIEW.replace("\"US\"","\"BR\"")))
                .andExpect(status().isUnprocessableContent());
        verify(client,never()).get(eq(ID),eq("US"),anyString(),anyString(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"AUTHORIZATION_REQUIRED","PERMISSION","TIMEOUT","RATE_LIMIT","INCOMPLETE"})
    void apiFailuresHaveSafeResponsesAndNoPersistence(String reason) throws Exception {
        when(client.get(anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenThrow(new AliExpressIntegrationException(AliExpressIntegrationException.Reason.valueOf(reason)));
        var response=mvc.perform(post(BASE+"/preview").with(admin()).contentType(MediaType.APPLICATION_JSON).content(PREVIEW))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(response.getContentAsString()).doesNotContain("synthetic-provider-token","stackTrace");assertThat(products.count()).isZero();
    }
    @Test void stockReviewOnlyAppliesToInactiveProductAndCannotExceedSource() throws Exception {
        var preview=service.preview(ID,"US","USD","en");service.importProduct(request(preview.fingerprint(),Map.of()));
        var product=products.findAll().get(0);var variant=variants.findBySupplierIgnoreCaseAndSupplierVariantId("ALIEXPRESS","201").orElseThrow();
        String path=BASE+"/products/"+product.getId()+"/variants/"+variant.getId()+"/stock";
        mvc.perform(patch(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supplierStockQuantity").value(4));
        mvc.perform(patch(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":99}"))
                .andExpect(status().isBadRequest());
        product.setActive(true);products.saveAndFlush(product);
        mvc.perform(patch(path).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest());
    }
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
    private static AliExpressAdminController.ImportRequest request(String fingerprint,Map<String,Integer> stocks) {
        return new AliExpressAdminController.ImportRequest(ID,"US","USD","en",fingerprint,"Produto comercial","Acessórios",
                Map.of("201",new BigDecimal("99.90"),"202",new BigDecimal("99.90"),"203",new BigDecimal("99.90"),"204",new BigDecimal("99.90")),stocks);
    }
    private static String importBody(String fingerprint,Map<String,Integer> stocks) throws Exception {
        return AliExpressProductNormalizerTests.JSON.writeValueAsString(request(fingerprint,stocks));
    }
}
