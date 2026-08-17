package com.garage.garageapi.integration.cj.client;

import com.garage.garageapi.integration.cj.dto.CjProductResponse;
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
    private CjApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        accessTokenHeader = new AtomicReference<>();
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
