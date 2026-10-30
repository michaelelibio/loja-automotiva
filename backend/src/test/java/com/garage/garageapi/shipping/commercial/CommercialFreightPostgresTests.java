package com.garage.garageapi.shipping.commercial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="INGARAGE_HML_POSTGRES",matches="true")
class CommercialFreightPostgresTests extends CommercialFreightIntegrationTests {
    @Autowired DataSource dataSource;@Autowired EntityManager entityManager;
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry r){
        String url=System.getenv("INGARAGE_HML_PG_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ingarage_homologation"))throw new IllegalStateException("Only the isolated local cluster is allowed");
        r.add("spring.datasource.url",()->url);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        r.add("spring.datasource.username",()->"ingarage_hml");r.add("spring.datasource.password",()->"");
        r.add("spring.jpa.hibernate.ddl-auto",()->"create");r.add("spring.sql.init.mode",()->"never");
    }
    @Test void jsonbNumericRoundtripPreservesDecisionAndAcceptedSnapshot() throws Exception {
        activate();String code=service.evaluate(request(1)).option().code();long id=create(code,UUID.randomUUID().toString());entityManager.flush();entityManager.clear();
        var stored=orders.findById(id).orElseThrow();stored.requireCommercialContextUnchanged();service.revalidate(stored);
        assertThat(service.evaluate(request(1)).option().code()).isEqualTo(code);assertThat(stored.getCommercialShipping().netProfitBrl()).isEqualByComparingTo("19.97");
    }
    @Test void actualAdditiveScriptRunsTwiceWithoutBackfillOrChangedTotals() throws Exception {
        activate();long id=create(service.evaluate(request(1)).option().code(),UUID.randomUUID().toString());entityManager.flush();
        var connection=DataSourceUtils.getConnection(dataSource);
        try(var stmt=connection.createStatement()){
            try(var row=stmt.executeQuery("select current_database(),host(inet_server_addr())")){row.next();assertThat(row.getString(1)).isEqualTo("ingarage_homologation");assertThat(row.getString(2)).isEqualTo("127.0.0.1");}
            stmt.execute("create schema commercial_audit");stmt.execute("create table commercial_audit.orders (like public.orders including defaults)");
            stmt.execute("insert into commercial_audit.orders select * from public.orders");stmt.execute("alter table commercial_audit.orders drop column commercial_shipping");
            stmt.execute("set local search_path to commercial_audit");
            for(int i=0;i<2;i++)ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V20261030_02__commercial_freight.sql"));
            try(var row=stmt.executeQuery("select total,commercial_shipping from commercial_audit.orders where id="+id)){row.next();assertThat(row.getBigDecimal(1)).isEqualByComparingTo("70");assertThat(row.getObject(2)).isNull();}
            try(var row=stmt.executeQuery("select data_type from information_schema.columns where table_schema='commercial_audit' and table_name='commercial_freight_tables' and column_name='document'")){row.next();assertThat(row.getString(1)).isEqualTo("jsonb");}
        }finally{try(var stmt=connection.createStatement()){stmt.execute("set local search_path to public");}DataSourceUtils.releaseConnection(connection,dataSource);}
    }
}
