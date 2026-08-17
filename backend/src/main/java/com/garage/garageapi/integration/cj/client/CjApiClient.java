package com.garage.garageapi.integration.cj.client;

import com.garage.garageapi.integration.cj.config.CjProperties;
import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class CjApiClient {

    private static final Logger logger = LoggerFactory.getLogger(CjApiClient.class);

    private static final String GET_TOKEN =
            "/api2.0/v1/authentication/getAccessToken";

    private static final String REFRESH_TOKEN =
            "/api2.0/v1/authentication/refreshAccessToken";

    private static final String PRODUCT_LIST =
            "/api2.0/v1/product/listV2";

    private static final String PRODUCT_QUERY =
            "/api2.0/v1/product/query";

    private static final JsonMapper DIAGNOSTIC_JSON =
            JsonMapper.builder().build();

    private final RestClient restClient;
    private final String baseUrl;
    private final boolean diagnosticLogging;

    @Autowired
    public CjApiClient(CjProperties properties) {
        this(
                restClient(properties),
                properties.getBaseUrl(),
                properties.isDiagnosticLogging()
        );

        logger.info(
                "CJ Dropshipping integration configured={}",
                StringUtils.hasText(properties.getApiKey())
        );
    }

    CjApiClient(RestClient restClient) {
        this(restClient, "<test-server>", true);
    }

    CjApiClient(
            RestClient restClient,
            String baseUrl,
            boolean diagnosticLogging
    ) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.diagnosticLogging = diagnosticLogging;
    }

    public CjTokenData authenticate(String apiKey) {
        logger.info("CJ Dropshipping authentication requested");

        return tokenRequest(
                GET_TOKEN,
                Map.of("apiKey", apiKey)
        );
    }

    public CjTokenData refresh(String refreshToken) {
        logger.info("CJ Dropshipping token refresh requested");

        return tokenRequest(
                REFRESH_TOKEN,
                Map.of("refreshToken", refreshToken)
        );
    }

    public CjProductResponse listProducts(
            String accessToken,
            String keyword,
            int page,
            int size
    ) {
        logProductRequest(keyword, page, size);

        try {
            ResponseEntity<JsonNode> entity = restClient
                    .get()
                    .uri(productUri(keyword, page, size))
                    .accept(MediaType.APPLICATION_JSON)
                    .header("CJ-Access-Token", accessToken)
                    .retrieve()
                    .toEntity(JsonNode.class);

            JsonNode response = entity.getBody();

            logProductResponse(entity, response);

            JsonNode data =
                    successfulData(response, "consulta de produtos");

            List<CjProductResponse.Product> products =
                    new ArrayList<>();

            for (JsonNode content : data.path("content")) {
                for (JsonNode product : content.path("productList")) {
                    products.add(mapProduct(product));
                }
            }

            logger.info(
                    "CJ Dropshipping product query completed; productCount={}",
                    products.size()
            );

            return new CjProductResponse(
                    integer(data, "pageNumber", page),
                    integer(data, "pageSize", size),
                    longValue(data, "totalRecords"),
                    longValue(data, "totalPages"),
                    List.copyOf(products)
            );

        } catch (RestClientResponseException exception) {
            logProductHttpException(exception);

            throw httpFailure(
                    "consulta de produtos",
                    exception
            );

        } catch (RestClientException exception) {
            logProductException(
                    exception,
                    CjIntegrationException.Reason.UPSTREAM
            );

            throw new CjIntegrationException(
                    "Falha temporária ao consultar produtos da CJ",
                    exception,
                    CjIntegrationException.Reason.UPSTREAM
            );

        } catch (CjIntegrationException exception) {
            logProductException(
                    exception,
                    exception.getReason()
            );

            throw exception;
        }
    }

    public CjProductResponse.Product getProduct(
            String accessToken,
            String productId
    ) {
        try {
            JsonNode response = restClient
                    .get()
                    .uri(builder -> builder
                            .path(PRODUCT_QUERY)
                            .queryParam("pid", productId)
                            .build()
                    )
                    .accept(MediaType.APPLICATION_JSON)
                    .header("CJ-Access-Token", accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode data =
                    successfulData(response, "consulta de produto");

            return new CjProductResponse.Product(
                    requiredText(data, "pid"),
                    text(data, "productNameEn"),
                    text(data, "bigImage"),
                    decimal(data, "sellPrice"),
                    text(data, "categoryId"),
                    text(data, "categoryName"),
                    text(data, "productSku")
            );

        } catch (RestClientResponseException exception) {
            throw httpFailure(
                    "consulta de produto",
                    exception
            );

        } catch (RestClientException exception) {
            throw new CjIntegrationException(
                    "Falha temporária ao consultar produto da CJ",
                    exception,
                    CjIntegrationException.Reason.UPSTREAM
            );
        }
    }

    private void logProductRequest(
            String keyword,
            int page,
            int size
    ) {
        if (!diagnosticLogging) {
            return;
        }

        logger.info(
                "CJ product list request diagnostic; "
                        + "baseUrl={}; path={}; page={}; size={}; "
                        + "keyWord={}; accessTokenPresent=true",
                baseUrl,
                PRODUCT_LIST,
                page,
                size,
                StringUtils.hasText(keyword)
                        ? keyword
                        : "<absent>"
        );
    }

    private void logProductResponse(
            ResponseEntity<JsonNode> entity,
            JsonNode response
    ) {
        if (!diagnosticLogging) {
            return;
        }

        JsonNode data =
                response == null
                        ? null
                        : response.path("data");

        JsonNode content =
                data == null
                        ? null
                        : data.path("content");

        JsonNode firstContent =
                content != null
                        && content.isArray()
                        && !content.isEmpty()
                        ? content.get(0)
                        : null;

        JsonNode productList =
                firstContent == null
                        ? null
                        : firstContent.path("productList");

        logger.info(
                "CJ product list response diagnostic; "
                        + "httpStatus={}; contentType={}; "
                        + "bodyPresent={}; result={}; code={}; "
                        + "message={}; requestId={}; "
                        + "dataPresent={}; dataFields={}; "
                        + "contentPresent={}; contentSize={}; "
                        + "firstContentFields={}; "
                        + "productListPresent={}; productListSize={}",
                entity.getStatusCode().value(),
                entity.getHeaders().getContentType(),
                response != null,
                safeValue(response, "result"),
                safeValue(response, "code"),
                safeValue(response, "message"),
                safeValue(response, "requestId"),
                present(data),
                fields(data),
                present(content),
                arraySize(content),
                fields(firstContent),
                present(productList),
                arraySize(productList)
        );
    }

    private void logProductHttpException(
            RestClientResponseException exception
    ) {
        if (!diagnosticLogging) {
            return;
        }

        JsonNode response =
                parseDiagnosticBody(
                        exception.getResponseBodyAsString()
                );

        JsonNode data =
                response == null
                        ? null
                        : response.path("data");

        logger.warn(
                "CJ product list HTTP exception diagnostic; "
                        + "exceptionClass={}; httpStatus={}; "
                        + "contentType={}; bodyPresent={}; "
                        + "result={}; code={}; message={}; "
                        + "requestId={}; dataPresent={}; dataFields={}",
                exception.getClass().getName(),
                exception.getStatusCode().value(),
                exception.getResponseHeaders() == null
                        ? null
                        : exception
                        .getResponseHeaders()
                        .getContentType(),
                StringUtils.hasText(
                        exception.getResponseBodyAsString()
                ),
                safeValue(response, "result"),
                safeValue(response, "code"),
                safeValue(response, "message"),
                safeValue(response, "requestId"),
                present(data),
                fields(data)
        );
    }

    private JsonNode parseDiagnosticBody(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }

        try {
            return DIAGNOSTIC_JSON.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void logProductException(
            Exception exception,
            CjIntegrationException.Reason reason
    ) {
        if (!diagnosticLogging) {
            return;
        }

        logger.warn(
                "CJ product list exception diagnostic; "
                        + "exceptionClass={}; reason={}; causeClass={}",
                exception.getClass().getName(),
                reason,
                exception.getCause() == null
                        ? "<absent>"
                        : exception
                        .getCause()
                        .getClass()
                        .getName()
        );
    }

    private String safeValue(
            JsonNode node,
            String field
    ) {
        if (node == null) {
            return "<absent>";
        }

        JsonNode value = node.path(field);

        return value.isMissingNode()
                || value.isNull()
                || !value.isValueNode()
                ? "<absent>"
                : value.asText();
    }

    private List<String> fields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return List.of();
        }

        return node
                .propertyNames()
                .stream()
                .sorted()
                .toList();
    }

    private boolean present(JsonNode node) {
        return node != null
                && !node.isMissingNode()
                && !node.isNull();
    }

    private int arraySize(JsonNode node) {
        return node != null && node.isArray()
                ? node.size()
                : 0;
    }

    private CjTokenData tokenRequest(
            String endpoint,
            Map<String, String> body
    ) {
        try {
            JsonNode response = restClient
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null
                    || !response.path("result").asBoolean(false)) {

                throw new CjIntegrationException(
                        "Autenticação com a CJ foi recusada",
                        CjIntegrationException.Reason.AUTHENTICATION
                );
            }

            JsonNode data = response.path("data");

            if (data.isMissingNode() || data.isNull()) {
                throw new CjIntegrationException(
                        "Resposta inválida da CJ durante autenticação",
                        CjIntegrationException.Reason.INVALID_RESPONSE
                );
            }

            String accessToken =
                    requiredText(data, "accessToken");

            String refreshToken =
                    requiredText(data, "refreshToken");

            return new CjTokenData(
                    accessToken,
                    instant(data, "accessTokenExpiryDate"),
                    refreshToken,
                    instant(data, "refreshTokenExpiryDate")
            );

        } catch (RestClientResponseException exception) {
            throw httpFailure(
                    "autenticação",
                    exception
            );

        } catch (RestClientException exception) {
            throw new CjIntegrationException(
                    "Falha temporária na autenticação com a CJ",
                    exception,
                    CjIntegrationException.Reason.UPSTREAM
            );
        }
    }

    private static RestClient restClient(
            CjProperties properties
    ) {
        HttpClient httpClient = HttpClient
                .newBuilder()
                .connectTimeout(
                        properties.getConnectTimeout()
                )
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                properties.getReadTimeout()
        );

        return RestClient
                .builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private Function<UriBuilder, java.net.URI> productUri(
            String keyword,
            int page,
            int size
    ) {
        return builder -> {
            builder
                    .path(PRODUCT_LIST)
                    .queryParam("page", page)
                    .queryParam("size", size);

            if (StringUtils.hasText(keyword)) {
                builder.queryParam(
                        "keyWord",
                        keyword
                );
            }

            return builder.build();
        };
    }

    private JsonNode successfulData(
            JsonNode response,
            String operation
    ) {
        if (response == null
                || !response.path("result").asBoolean(false)
                || response.path("data").isMissingNode()
                || response.path("data").isNull()) {

            throw new CjIntegrationException(
                    "Resposta inválida da CJ durante " + operation,
                    CjIntegrationException.Reason.INVALID_RESPONSE
            );
        }

        return response.path("data");
    }

    private CjProductResponse.Product mapProduct(
            JsonNode product
    ) {
        String id =
                requiredText(product, "id");

        return new CjProductResponse.Product(
                id,
                text(product, "nameEn"),
                text(product, "bigImage"),
                decimal(product, "sellPrice"),
                text(product, "categoryId"),
                text(product, "threeCategoryName"),
                text(product, "sku")
        );
    }

    private CjIntegrationException httpFailure(
            String operation,
            RestClientResponseException exception
    ) {
        int status =
                exception.getStatusCode().value();

        logger.warn(
                "CJ Dropshipping request failed; "
                        + "operation={}; httpStatus={}",
                operation,
                status
        );

        if (status == 401 || status == 403) {
            return new CjIntegrationException(
                    "Autenticação com a CJ foi recusada",
                    exception,
                    CjIntegrationException.Reason.AUTHENTICATION
            );
        }

        if (status == 429) {
            return new CjIntegrationException(
                    "Limite de requisições da CJ atingido",
                    exception,
                    CjIntegrationException.Reason.RATE_LIMIT
            );
        }

        return new CjIntegrationException(
                "Falha temporária na API da CJ",
                exception,
                CjIntegrationException.Reason.UPSTREAM
        );
    }

    private String requiredText(
            JsonNode node,
            String field
    ) {
        String value =
                text(node, field);

        if (!StringUtils.hasText(value)) {
            throw new CjIntegrationException(
                    "Resposta inválida da CJ",
                    CjIntegrationException.Reason.INVALID_RESPONSE
            );
        }

        return value;
    }

    private String text(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.path(field);

        return value.isMissingNode()
                || value.isNull()
                ? null
                : value.asText();
    }

    private Instant instant(
            JsonNode node,
            String field
    ) {
        try {
            return OffsetDateTime
                    .parse(
                            requiredText(node, field)
                    )
                    .toInstant();

        } catch (DateTimeParseException exception) {
            throw new CjIntegrationException(
                    "Data de expiração inválida retornada pela CJ",
                    exception,
                    CjIntegrationException.Reason.INVALID_RESPONSE
            );
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);

        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.contains("--")) {
            normalized = normalized.split("--")[0].trim();
        } else if (normalized.matches("^\\d+(?:\\.\\d+)?\\s*-\\s*\\d+(?:\\.\\d+)?$")) {
            normalized = normalized.split("\\s*-\\s*")[0].trim();
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            throw new CjIntegrationException(
                    "Preço inválido retornado pela CJ",
                    exception,
                    CjIntegrationException.Reason.INVALID_RESPONSE
            );
        }
    }

    private int integer(
            JsonNode node,
            String field,
            int fallback
    ) {
        JsonNode value =
                node.path(field);

        return value.isNumber()
                ? value.asInt()
                : fallback;
    }

    private long longValue(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.path(field);

        return value.isNumber()
                ? value.asLong()
                : 0L;
    }
}
