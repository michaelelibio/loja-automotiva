package com.garage.garageapi.catalog;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in only: disposable local cluster, fixed loopback address and dedicated schema. */
@EnabledIfEnvironmentVariable(named="INGARAGE_CATALOG_POSTGRES",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://127.0.0.1:55461/postgres?currentSchema=catalog_verify",
    "spring.datasource.driver-class-name=org.postgresql.Driver","spring.datasource.username=catalog_test","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","aliexpress.orders.enabled=false",
    "aliexpress.orders.auto-dispatch-enabled=false","mercadopago.reconciliation.enabled=false","app.payment.creation-enabled=false"})
class CatalogPostgresMigrationTests {
    @Autowired ProductRepository products; @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf; @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void migrateLegacyRowsTwiceThenHibernateValidate() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{assertThat(connection.getMetaData().getURL()).startsWith("jdbc:postgresql://127.0.0.1:55461/");return null;});
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
        var product=products.saveAndFlush(new Product("Produto de teste","catalog-pg-test",null,null,BigDecimal.TEN,null,"Categoria original",7,null,true));
        var original=jdbc.queryForMap("select id,name,slug,category,stock_quantity,price,active,created_at from products where id=?",product.getId());
        em.clear();
        jdbc.execute("drop table product_catalog_surfaces,product_catalog_purposes,product_catalog_search_terms");
        jdbc.execute("drop table catalog_categories cascade");
        jdbc.execute("alter table products drop column catalog_category_id,drop column catalog_subcategory_id,drop column brand,drop column catalog_usage,drop column concentrated,drop column refill,drop column featured,drop column beginner,drop column commercial_origin,drop column published_at");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:postgresql://127.0.0.1:55461/");
            var migration=new ClassPathResource("db/migration/V20261116_01__detailing_catalog.sql");
            ScriptUtils.executeSqlScript(connection,migration);ScriptUtils.executeSqlScript(connection,migration);return null;
        });
        assertThat(jdbc.queryForMap("select id,name,slug,category,stock_quantity,price,active,created_at from products where id=?",product.getId())).isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from catalog_categories",Integer.class)).isEqualTo(48);
        assertThat(jdbc.queryForMap("select catalog_category_id,brand,commercial_origin,published_at from products where id=?",product.getId()).values()).containsOnlyNulls();
        });
        // Every mapping, including the existing order/payment/supplier mappings, must remain compatible.
        emf.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
    }
}
