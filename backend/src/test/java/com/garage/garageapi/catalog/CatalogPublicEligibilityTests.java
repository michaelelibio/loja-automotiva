package com.garage.garageapi.catalog;

import com.garage.garageapi.product.entity.*;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.integration.aliexpress.AliExpressOrderClient;
import com.garage.garageapi.payment.gateway.CheckoutProGateway;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("clean-checkout")
@Transactional
class CatalogPublicEligibilityTests {
    @Autowired ProductRepository products;
    @Autowired CatalogService catalog;
    @Autowired EntityManager em;
    @MockitoBean AliExpressOrderClient supplier;
    @MockitoBean CheckoutProGateway gateway;

    Product product(String group,String supplierName,FulfillmentType fulfillment,boolean active) {
        var p=new Product(group,"public-paging-"+UUID.randomUUID(),null,null,BigDecimal.TEN,null,"Legada",5,null,active);
        p.linkSupplier(supplierName,"supplier-"+UUID.randomUUID(),null,null,Instant.now());p.configureFulfillment(fulfillment);
        return products.saveAndFlush(p);
    }
    void variant(Product p,int stock,String key,String supplierProductId) {
        var v=new ProductVariant(p,"ALIEXPRESS","sku-"+UUID.randomUUID(),supplierProductId,null,"Único",Map.of(),key,BigDecimal.ONE,"BRL",null,null,null,null,null);
        v.setSupplierStockQuantity(stock);em.persist(v);p.getVariants().add(v);
    }
    CatalogService.Query query(String group,int page) {
        return new CatalogService.Query(group,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"lowest",page,null);
    }
    @Test void productionPolicyAndSkuAvailabilityApplyToTheWholeSelectionBeforeCountingAndPaging() {
        String group="PublicPaging"+UUID.randomUUID().toString().replace("-","");
        var expected=new ArrayList<Long>();
        for(int i=0;i<13;i++) {
            var p=product(group,"aliexpress",FulfillmentType.DROPSHIPPING,true);
            variant(p,5,"",p.getSupplierProductId());expected.add(p.getId());
            if(i==0)variant(p,5,"",p.getSupplierProductId());
        }
        product(group,"CJ",FulfillmentType.DROPSHIPPING,true);
        product(group,"ALIEXPRESS",FulfillmentType.LOCAL_STOCK,true);
        product(group,"ALIEXPRESS",FulfillmentType.DROPSHIPPING,false);
        product(group,"ALIEXPRESS",FulfillmentType.DROPSHIPPING,true);
        var noStock=product(group,"ALIEXPRESS",FulfillmentType.DROPSHIPPING,true);variant(noStock,0,"",noStock.getSupplierProductId());
        var missingSelection=product(group,"ALIEXPRESS",FulfillmentType.DROPSHIPPING,true);variant(missingSelection,5,null,missingSelection.getSupplierProductId());
        var wrongLink=product(group,"ALIEXPRESS",FulfillmentType.DROPSHIPPING,true);variant(wrongLink,5,"","other-supplier-product");
        em.flush();
        var first=catalog.browse(query(group,0));var second=catalog.browse(query(group,1));
        assertThat(first.size()).isEqualTo(12);assertThat(first.totalElements()).isEqualTo(13);assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).hasSize(12);assertThat(second.content()).hasSize(1);
        var found=new ArrayList<>(first.content());found.addAll(second.content());
        assertThat(found).allMatch(p->p.active()&&p.availableForSale());
        assertThat(found.stream().map(p->p.id()).toList()).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        verifyNoInteractions(supplier,gateway);
    }
}
