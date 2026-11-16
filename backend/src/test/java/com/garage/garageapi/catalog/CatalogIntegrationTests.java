package com.garage.garageapi.catalog;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.dto.ProductResponse;
import com.garage.garageapi.user.entity.User;
import com.garage.garageapi.user.repository.UserRepository;
import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.address.repository.AddressRepository;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.payment.entity.Payment;
import com.garage.garageapi.payment.entity.PaymentMethod;
import com.garage.garageapi.payment.entity.PaymentStatus;
import com.garage.garageapi.payment.repository.PaymentRepository;
import com.garage.garageapi.integration.aliexpress.AliExpressOrderClient;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogIntegrationTests {
    @Autowired CatalogService service; @Autowired CatalogCategoryRepository categories;
    @Autowired ProductRepository products; @Autowired MockMvc mvc; @Autowired EntityManager em;
    @Autowired UserRepository users; @Autowired AddressRepository addresses; @Autowired OrderRepository orders; @Autowired PaymentRepository payments;
    @MockitoBean AliExpressOrderClient supplier; @MockitoBean CheckoutProGateway gateway;
    CatalogCategory root,sub,empty; Product a,b;
    @BeforeEach void fixture() {
        String suffix=UUID.randomUUID().toString();
        root=categories.saveAndFlush(new CatalogCategory("Lavagem","lavagem-"+suffix,null,null,1,true));
        sub=categories.saveAndFlush(new CatalogCategory("Shampoos","shampoos",root,null,1,true));
        empty=categories.saveAndFlush(new CatalogCategory("Interior","interior-"+suffix,null,null,2,true));
        a=product("Shampoo Ágil",new BigDecimal("20.00"));b=product("Borrifador",new BigDecimal("10.00"));
        service.apply(a,metadata("Ágil",true));service.apply(b,metadata("Outra",false));em.flush();
    }
    @AfterEach void noExternalEffects(){verifyNoInteractions(supplier);}
    Product product(String name,BigDecimal price){return products.saveAndFlush(new Product(name,"catalog-"+UUID.randomUUID(),null,null,price,null,"Categoria legada",5,null,true));}
    CatalogProductRequest metadata(String brand,boolean featured){return new CatalogProductRequest(root.getId(),sub.getId(),brand,List.of("Pintura"),List.of("Limpeza"),List.of("espuma ativa"),"BOTH",true,null,featured,true,null);}
    @Test void browseCombinesAccentInsensitiveSearchFacetsAndDatabasePagination() throws Exception {
        mvc.perform(get("/api/catalog").param("q","agil").param("category",root.getPath()).param("surface","Pintura").param("purpose","Limpeza").param("minPrice","15").param("maxPrice","30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].name").value("Shampoo Ágil"))
            .andExpect(jsonPath("$.facets.brands[0]").value("Ágil")).andExpect(jsonPath("$.content[0].costPrice").doesNotExist());
        mvc.perform(get("/api/catalog").param("q","espuma ativa").param("category",root.getPath()).param("size","1").param("sort","lowest").param("page","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].id").value(a.getId()));
    }
    @Test void irrelevantOrUnavailableOptionsAreAbsentAndWildcardsLiteral() throws Exception {
        b.setActive(false);em.flush();
        mvc.perform(get("/api/catalog").param("category",root.getPath())).andExpect(jsonPath("$.facets.brands.length()").value(1));
        mvc.perform(get("/api/catalog").param("q","%_")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        a.decreaseStock(5);em.flush();assertThat(service.categoryList(false)).noneMatch(c->c.id().equals(root.getId()));
    }
    @Test void emptyAndDisabledCategoriesHiddenWithoutDeletingProducts() {
        assertThat(service.categoryList(true)).anyMatch(c->c.id().equals(empty.getId()));
        assertThat(service.categoryList(false)).noneMatch(c->c.id().equals(empty.getId()));
        root.update(root.getName(),null,1,false);em.flush();
        assertThat(service.categoryList(false)).noneMatch(c->c.id().equals(root.getId())||c.id().equals(sub.getId()));
        assertThat(ProductResponse.from(a).catalog().category()).isNull();
        assertThat(a.getCategory()).isEqualTo("Categoria legada");assertThat(products.findById(a.getId())).isPresent();
    }
    @Test void bulkChangesOnlySelectedFieldsAndKeepsSupplierStockLegacyAndOtherMetadata() {
        a.linkSupplier("ALIEXPRESS","supplier-"+UUID.randomUUID(),null,null,Instant.now());a.configureFulfillment(FulfillmentType.DROPSHIPPING);
        String provider=a.getSupplierProductId();
        service.bulk(new CatalogService.Bulk(List.of(a.getId(),b.getId()),Set.of("featured"),new CatalogProductRequest(null,null,null,null,null,null,null,null,null,false,false,null)));
        em.flush();em.clear();var saved=products.findById(a.getId()).orElseThrow();
        assertThat(saved.isFeatured()).isFalse();assertThat(saved.getBrand()).isEqualTo("Ágil");assertThat(saved.getStockQuantity()).isEqualTo(5);
        assertThat(saved.getSupplierProductId()).isEqualTo(provider);assertThat(saved.getCategory()).isEqualTo("Categoria legada");
    }
    @Test void invalidSubcategoryOriginAndIncompleteBulkAreRejected() {
        var invalid=new CatalogProductRequest(empty.getId(),sub.getId(),null,null,null,null,null,null,null,false,false,null);
        assertThatThrownBy(()->service.classify(a.getId(),invalid)).isInstanceOf(ResourceConflictException.class);
    }
    @Test void apiRequiresAdminAndValidatesValues() throws Exception {
        mvc.perform(get("/api/admin/catalog/categories")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/catalog/products/"+a.getId()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/catalog/categories").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"name\":\"Teste\",\"slug\":\"../ruim\",\"position\":0,\"active\":true}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog?size=101")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/catalog/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json")
            .content("{\"productIds\":[null],\"fields\":[\"brand\"],\"catalog\":{\"brand\":\"Teste\"}}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/catalog/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json")
            .content("{\"productIds\":[1],\"fields\":[null],\"catalog\":{\"brand\":\"Teste\"}}")).andExpect(status().isBadRequest());
    }
    @Test void categoryOrderAndDeactivationKeepStableUrl() {
        service.saveCategory(root.getId(),new CatalogService.CategoryRequest("Nome atualizado",root.getSlug(),"Descrição",null,40,false));
        assertThat(root.getPath()).isEqualTo(root.getSlug());assertThat(root.getPosition()).isEqualTo(40);assertThat(root.isActive()).isFalse();
    }
    @Test void publicationDateTracksFirstPublicationNotLastEdit() {
        var p=product("Rascunho",BigDecimal.TEN);p.setActive(false); // Simulate a migrated legacy product with no known publication date.
        org.springframework.test.util.ReflectionTestUtils.setField(p,"publishedAt",null);p.setActive(true);var first=p.getPublishedAt();
        assertThat(first).isNotNull();p.setActive(false);p.setActive(true);assertThat(p.getPublishedAt()).isEqualTo(first);
    }
    @Test void bestsellersRequireRealVerifiedPaymentsAndExcludeReviewAndTestMode() throws Exception {
        var u=users.save(User.local("Teste","catalog-"+UUID.randomUUID()+"@example.test","hash"));
        var address=addresses.save(new Address(u,"Teste","Teste","01001000","Rua de teste","1",null,"Centro","São Paulo","SP",true));
        var o=new Order(u,address,new BigDecimal("20.00"),BigDecimal.ZERO,Duration.ofHours(1));o.addItem(new OrderItem(o,a,1,a.getPrice(),a.getPrice()));o.markPaid();orders.saveAndFlush(o);
        var p=new Payment(o,PaymentMethod.MERCADO_PAGO);p.applyProviderResult("mock","mock",PaymentStatus.PAID,null,null,null,Instant.now());p.recordProviderEnvironment(false,Instant.now());payments.saveAndFlush(p);
        mvc.perform(get("/api/catalog?section=bestsellers")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        p.recordProviderEnvironment(true,Instant.now());em.flush();mvc.perform(get("/api/catalog?section=bestsellers")).andExpect(jsonPath("$.content[0].id").value(a.getId()));
        o.requirePaymentReview();em.flush();mvc.perform(get("/api/catalog?section=bestsellers")).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void shelvesUseOnlyExplicitMetadata() throws Exception {
        mvc.perform(get("/api/catalog").param("section","featured").param("category",root.getPath())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/catalog").param("section","professional").param("category",root.getPath())).andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test void adminDraftClassificationPublicationDiscoveryAndMockCheckoutPreserveExistingFlow() throws Exception {
        var u=users.save(User.google("Cliente Teste","flow-"+UUID.randomUUID()+"@example.test","subject-"+UUID.randomUUID(),null));
        var admin=jwt().jwt(j->j.subject(u.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var customer=jwt().jwt(j->j.subject(u.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));
        String draft="{\"name\":\"Produto de teste\",\"slug\":\"flow-"+UUID.randomUUID()+"\",\"price\":20,\"costPrice\":5,\"stock\":5,\"active\":false,\"category\":\"Categoria legada\",\"sku\":\"FLOW-"+UUID.randomUUID()+"\",\"catalog\":{\"categoryId\":"+root.getId()+",\"subcategoryId\":"+sub.getId()+",\"brand\":\"Confirmada\",\"featured\":true}}";
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var created=json.readTree(mvc.perform(post("/api/admin/products").with(admin).contentType("application/json").content(draft)).andExpect(status().isCreated()).andExpect(jsonPath("$.catalog.brand").value("Confirmada")).andReturn().getResponse().getContentAsString());
        long id=created.path("id").asLong();
        mvc.perform(get("/api/products/"+id)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/products/"+id+"/active").with(admin).contentType("application/json").content("{\"active\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/catalog").param("category",root.getPath()).param("brand","Confirmada")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        var address=addresses.save(new Address(u,"Teste","Cliente Teste","01001000","Rua Teste","1",null,"Centro","São Paulo","SP",true));
        var order=json.readTree(mvc.perform(post("/api/orders").with(customer).contentType("application/json").content("{\"addressId\":"+address.getId()+",\"items\":[{\"productId\":"+id+",\"quantity\":2}]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.subtotal").value(40)).andExpect(jsonPath("$.shippingCost").value(18.90)).andExpect(jsonPath("$.status").value("PENDING_PAYMENT")).andReturn().getResponse().getContentAsString());
        long orderId=order.path("id").asLong();
        org.mockito.Mockito.when(gateway.createPreference(org.mockito.ArgumentMatchers.any())).thenAnswer(inv->{var req=inv.getArgument(0,CheckoutProGateway.PreferenceRequest.class);return new CheckoutProGateway.PreferenceResult("preference-mock",req.paymentId().toString(),"https://sandbox.mercadopago.com.br/mock");});
        mvc.perform(post("/api/orders/"+orderId+"/payments").with(customer).contentType("application/json").content("{\"method\":\"MERCADO_PAGO\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.checkoutUrl").value("https://sandbox.mercadopago.com.br/mock"));
        assertThat(products.findById(id).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(products.findById(id).orElseThrow().getCategory()).isEqualTo("Categoria legada");
        org.mockito.Mockito.verify(gateway).createPreference(org.mockito.ArgumentMatchers.argThat(req->req.total().compareTo(new BigDecimal("58.90"))==0));
    }
}
