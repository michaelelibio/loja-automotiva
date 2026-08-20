package com.garage.garageapi.integration.cj.client;

import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.dto.CjProductVariantsResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CjApiClientTests {
    private HttpServer server;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> accessTokenHeader;
    private AtomicReference<String> variantProductId;
    private CjApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        accessTokenHeader = new AtomicReference<>();
        variantProductId = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api2.0/v1/authentication/getAccessToken", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, tokenResponse("ACCESS", "REFRESH"));
        });
        server.createContext("/api2.0/v1/authentication/refreshAccessToken", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, tokenResponse("RENEWED", "NEW_REFRESH"));
        });
        server.createContext("/api2.0/v1/product/listV2", exchange -> {
            accessTokenHeader.set(exchange.getRequestHeaders().getFirst("CJ-Access-Token"));
            respond(exchange, 200, """
                    {"code":200,"result":true,"message":"Success","data":{
                      "pageSize":1,"pageNumber":1,"totalRecords":1,"totalPages":1,
                      "content":[{"productList":[{
                        "id":"CJ-ID","nameEn":"Car cleaner","sku":"CJ-SKU",
                        "bigImage":"https://example.test/image.jpg","sellPrice":"12.34",
                        "categoryId":"CATEGORY","threeCategoryName":"Car Care"
                      }]}]}}
                    """);
        });
                server.createContext("/api2.0/v1/product/variant/query", exchange -> {
                    accessTokenHeader.set(exchange.getRequestHeaders().getFirst("CJ-Access-Token"));
                    variantProductId.set(exchange.getRequestURI().getQuery());
                    respond(exchange, 200, """
                        {"code":200,"result":true,"message":"Success","data":[
                          {"vid":"VID-1","pid":"PID-1","variantNameEn":"Black XL",
                           "variantSku":"SKU-1","variantImage":"https://example.test/black.jpg",
                           "variantKey":"Black-XL","variantStandard":"long=10,width=5,height=2",
                           "variantLength":10,"variantWidth":5,"variantHeight":2,"variantVolume":100,
                           "variantWeight":3.25,"variantSellPrice":12.34},
                          {"vid":"VID-2","pid":"PID-1","variantName":"Red",
                           "variantSku":"SKU-2","variantKey":"Red","variantSellPrice":"13.50"}
                        ]}
                        """);
                });
        server.start();
        client = new CjApiClient(RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void authenticatesWithApiKeyInJsonBody() {
        CjTokenData token = client.authenticate("TEST_API_KEY");

        assertThat(token.accessToken()).isEqualTo("ACCESS");
        assertThat(token.refreshToken()).isEqualTo("REFRESH");
        assertThat(requestBody.get()).contains("\"apiKey\":\"TEST_API_KEY\"");
    }

    @Test
    void refreshesUsingRefreshTokenInJsonBody() {
        CjTokenData token = client.refresh("OLD_REFRESH");

        assertThat(token.accessToken()).isEqualTo("RENEWED");
        assertThat(requestBody.get()).contains("\"refreshToken\":\"OLD_REFRESH\"");
    }

    @Test
    void mapsProductListWithoutExposingToken() {
        CjProductResponse response = client.listProducts("SECRET_ACCESS", "car", 1, 1);

        assertThat(accessTokenHeader.get()).isEqualTo("SECRET_ACCESS");
        assertThat(response.products()).singleElement().satisfies(product -> {
            assertThat(product.cjProductId()).isEqualTo("CJ-ID");
            assertThat(product.name()).isEqualTo("Car cleaner");
            assertThat(product.priceUsd()).isEqualByComparingTo("12.34");
        });
        assertThat(response.toString()).doesNotContain("SECRET_ACCESS");
    }

    @Test
    void mapsSingleAndMultipleVariantsUsingPid() {
        CjProductVariantsResponse response = client.getProductVariants("SECRET_ACCESS", "PID-1");

        assertThat(accessTokenHeader.get()).isEqualTo("SECRET_ACCESS");
        assertThat(variantProductId.get()).isEqualTo("pid=PID-1");
        assertThat(response.productId()).isEqualTo("PID-1");
        assertThat(response.variants()).hasSize(2);
        assertThat(response.variants().get(0).cjVariantId()).isEqualTo("VID-1");
        assertThat(response.variants().get(0).cjProductId()).isEqualTo("PID-1");
        assertThat(response.variants().get(0).priceUsd()).isEqualByComparingTo("12.34");
        assertThat(response.variants().get(0).attributes())
                .containsEntry("option1", "Black")
                .containsEntry("option2", "XL");
        assertThat(response.variants().get(0).weightGrams()).isEqualByComparingTo("3.25");
        assertThat(response.variants().get(1).name()).isEqualTo("Red");
    }

    @Test
    void acceptsEmptyVariantResponse() throws IOException {
        server.removeContext("/api2.0/v1/product/variant/query");
        server.createContext("/api2.0/v1/product/variant/query", exchange ->
                respond(exchange, 200, "{\"code\":200,\"result\":true,\"data\":[]}"));

        assertThat(client.getProductVariants("TOKEN", "EMPTY").variants()).isEmpty();
    }

    @Test
    void rejectsInvalidVariantJson() throws IOException {
        server.removeContext("/api2.0/v1/product/variant/query");
        server.createContext("/api2.0/v1/product/variant/query", exchange ->
                respond(exchange, 200, "not-json"));

        assertThatThrownBy(() -> client.getProductVariants("TOKEN", "INVALID"))
                .isInstanceOf(CjIntegrationException.class)
                .hasMessage("Falha temporária ao consultar variantes da CJ");
    }

    @Test
    void classifiesVariantAuthenticationRateLimitAndUpstreamFailures() throws IOException {
        for (int status : new int[]{401, 403, 429, 500}) {
            server.removeContext("/api2.0/v1/product/variant/query");
            server.createContext("/api2.0/v1/product/variant/query", exchange ->
                    respond(exchange, status, "{}"));

            assertThatThrownBy(() -> client.getProductVariants("TOKEN", "FAIL"))
                    .isInstanceOfSatisfying(CjIntegrationException.class, exception -> {
                        CjIntegrationException.Reason expected = status == 429
                                ? CjIntegrationException.Reason.RATE_LIMIT
                                : status == 401 || status == 403
                                ? CjIntegrationException.Reason.AUTHENTICATION
                                : CjIntegrationException.Reason.UPSTREAM;
                        assertThat(exception.getReason()).isEqualTo(expected);
                    });
        }
    }

    @Test
    void rejectsUnsuccessfulCjEnvelope() throws IOException {
        server.removeContext("/api2.0/v1/product/listV2");
        server.createContext("/api2.0/v1/product/listV2", exchange ->
                respond(exchange, 200, "{\"code\":1600100,\"result\":false,\"data\":null}"));

        assertThatThrownBy(() -> client.listProducts("TOKEN", null, 1, 1))
                .isInstanceOf(CjIntegrationException.class)
                .hasMessage("Resposta inválida da CJ durante consulta de produtos");
    }

    @Test
    void classifiesAuthenticationFailureReturnedWithHttp200() {
        server.removeContext("/api2.0/v1/authentication/getAccessToken");
        server.createContext("/api2.0/v1/authentication/getAccessToken", exchange ->
                respond(exchange, 200,
                        "{\"code\":1600001,\"result\":false,\"data\":null}"));

        assertThatThrownBy(() -> client.authenticate("INVALID_KEY"))
                .isInstanceOfSatisfying(CjIntegrationException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo(CjIntegrationException.Reason.AUTHENTICATION));
    }

    private String tokenResponse(String accessToken, String refreshToken) {
        return """
                {"code":200,"result":true,"message":"Success","data":{
                  "accessToken":"%s","accessTokenExpiryDate":"2099-01-01T00:00:00+00:00",
                  "refreshToken":"%s","refreshTokenExpiryDate":"2099-06-01T00:00:00+00:00"
                }}
                """.formatted(accessToken, refreshToken);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
