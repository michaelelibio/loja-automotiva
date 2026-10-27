package com.garage.garageapi.integration.aliexpress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.*;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class AliExpressTerritorialClientTests {
    MockRestServiceServer server;
    AliExpressTerritorialClient client;
    static final JsonMapper JSON=JsonMapper.builder().build();
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api-sg.aliexpress.com");
        server=MockRestServiceServer.bindTo(builder).build();
        client=new AliExpressTerritorialClient(builder.build(),"synthetic-key","synthetic-secret",Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
    }
    String body(Object children) {
        return JSON.writeValueAsString(Map.of("result",Map.of("ret","true","data",Map.of("country","BR","type","country","children",children))));
    }
    @Test void queriesOnlyOfficialReadMethodWithBRPersistedTokenAndExactSignature(CapturedOutput output) {
        server.expect(requestTo("https://api-sg.aliexpress.com/sync")).andExpect(method(HttpMethod.POST)).andExpect(request->{
            Map<String,String> params=new TreeMap<>();
            for(String part:((MockClientHttpRequest)request).getBodyAsString().split("&")) {
                var pair=part.split("=",2);params.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
            }
            String sign=params.remove("sign");
            assertThat(params).containsEntry("method","aliexpress.ds.address.get").containsEntry("countryCode","BR")
                    .containsEntry("session","synthetic-token").containsEntry("isMultiLanguage","true").containsEntry("v","2.0");
            assertThat(params).doesNotContainKeys("zip","language","productId","address","access_token","app_secret");
            try {
                StringBuilder canonical=new StringBuilder();params.forEach((k,v)->canonical.append(k).append(v));
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("synthetic-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                assertThat(sign).isEqualTo(HexFormat.of().withUpperCase().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8))));
            } catch(Exception ex) { throw new AssertionError(ex); }
        }).andRespond(withSuccess(body("[]"),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.applicationAccess()).isEqualTo("CALL_ACCEPTED");
        assertThat(result.country()).isEqualTo("BR");assertThat(result.matches()).isEmpty();
        assertThat(result.limitations()).contains("DIAGNOSTIC_ONLY_CHECKOUT_BLOCKED","POSTAL_CODE_SUPPORT_NOT_CONFIRMED");
        assertThat(output.getAll()+result).doesNotContain("synthetic-token","synthetic-secret");server.verify();
    }
    @Test void nestedJsonStringPreservesObservedIdsAndAncestryWithoutInferringFreightMapping() {
        var city=Map.of("id","0099","name","Joinville","cityCode","city-example");
        var state=Map.of("code","STATE-EXAMPLE","name","Santa Catarina","children",JSON.writeValueAsString(List.of(city)));
        server.expect(anything()).andRespond(withSuccess(body(JSON.writeValueAsString(List.of(state))),MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.safeCode()).isEqualTo("TERRITORIAL_QUERY_SUCCEEDED");
        assertThat(result.matches()).extracting(AliExpressTerritorialDiagnosticResult.Match::target).containsExactly("SANTA_CATARINA","JOINVILLE");
        var joinville=result.matches().get(1);
        assertThat(joinville.observed().fields()).contains(new AliExpressTerritorialDiagnosticResult.Scalar("id","STRING","0099"));
        assertThat(joinville.ancestors()).anySatisfy(n->assertThat(n.fields()).contains(new AliExpressTerritorialDiagnosticResult.Scalar("code","STRING","STATE-EXAMPLE")));
        assertThat(result.limitations()).contains("TERRITORIAL_IDS_NOT_FREIGHT_MAPPING");server.verify();
    }
    @Test void wrapperAndArrayChildrenAreSupportedButOnlyAllowlistedScalarsEscape(CapturedOutput output) {
        String response=body(List.of(Map.of("name","Joinville","id",123,"recipient","private-name","token","synthetic-token","address","private-street")));
        server.expect(anything()).andRespond(withSuccess("{\"aliexpress_ds_address_get_response\":"+response+"}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).observed().fields()).contains(new AliExpressTerritorialDiagnosticResult.Scalar("id","NUMBER","123"));
        assertThat(result+output.getAll()).doesNotContain("private-name","private-street","synthetic-token","recipient");server.verify();
    }
    @ParameterizedTest @CsvSource({"11,isv.permission-api-package-limit,PERMISSION_DENIED","27,invalid-sessionkey,TOKEN_REJECTED",
            "IllegalAccessToken,unknown,TOKEN_REJECTED",
            "25,unknown,PROTOCOL_ERROR","7,unknown,RATE_LIMITED","50,unknown,PROVIDER_REJECTED"})
    void knownPlatformRejectionsDoNotProveOtherPermissions(String code,String sub,String status,CapturedOutput output) {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":\""+code+"\",\"sub_code\":\""+sub+"\",\"msg\":\"synthetic-token synthetic-secret\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.status().name()).isEqualTo(status);assertThat(result.applicationAccess()).isEqualTo("NOT_CONFIRMED");
        assertThat(result.providerCode()).isEqualTo(code);
        assertThat(result+output.getAll()).doesNotContain("synthetic-token","synthetic-secret");server.verify();
    }
    @Test void malformedEmbeddedJsonIsNotAnAbsentCityOrAnAcceptedTree() {
        server.expect(anything()).andRespond(withSuccess(body("not-json"),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").safeCode()).isEqualTo("UNSUPPORTED_TERRITORIAL_STRUCTURE");server.verify();
    }
    @Test void nonBRResponseCannotProduceBRMatch() {
        server.expect(anything()).andRespond(withSuccess(body("[]").replace("\"BR\"","\"US\""),MediaType.APPLICATION_JSON));
        assertThat(client.query("synthetic-token").safeCode()).isEqualTo("COUNTRY_NOT_CONFIRMED");server.verify();
    }
    @Test void failedBusinessResultNeverBecomesPermissionSuccess() {
        server.expect(anything()).andRespond(withSuccess("{\"result\":{\"ret\":false,\"code\":\"11\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");assertThat(result.safeCode()).isEqualTo("ADDRESS_QUERY_REJECTED");
        assertThat(result.applicationAccess()).isEqualTo("NOT_CONFIRMED");server.verify();
    }
    @Test void missingLocalTokenCannotTriggerEvenThoughOfficialTokenIsOptional() {
        assertThat(client.query(null).safeCode()).isEqualTo("LOCAL_TOKEN_UNAVAILABLE");server.verify();
    }
    @Test void malformedResponseAndTimeoutRemainFixedFailures() {
        server.expect(anything()).andRespond(withSuccess("not-json",MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("synthetic-token")));
        assertThat(client.query("synthetic-token").safeCode()).isEqualTo("INVALID_JSON_RESPONSE");
        assertThat(client.query("synthetic-token").safeCode()).isEqualTo("COMMUNICATION_FAILURE");server.verify();
    }
    @Test void deeplyNestedTreeAndOversizedResponseFailWithoutPartialTerritories() {
        Object tree=List.of(Map.of("name","Joinville"));
        for(int i=0;i<25;i++) tree=Map.of("children",tree);
        server.expect(anything()).andRespond(withSuccess(body(tree),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(" ".repeat(4*1024*1024+1),MediaType.APPLICATION_JSON));
        var deep=client.query("synthetic-token");
        assertThat(deep.safeCode()).isEqualTo("UNSUPPORTED_TERRITORIAL_STRUCTURE");assertThat(deep.matches()).isEmpty();
        assertThat(client.query("synthetic-token").safeCode()).isEqualTo("RESPONSE_TOO_LARGE");server.verify();
    }
    @Test void providerCodesCannotCopyFreeTextOrCredentials() {
        server.expect(anything()).andRespond(withSuccess("{\"error_response\":{\"code\":\"synthetic-token\",\"sub_code\":\"CPF 52998224725\"}}",MediaType.APPLICATION_JSON));
        var result=client.query("synthetic-token");
        assertThat(result.providerCode()).isNull();assertThat(result.providerSubCode()).isNull();server.verify();
    }
}
