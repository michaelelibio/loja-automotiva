package com.garage.garageapi.integration.cj.client;

import com.garage.garageapi.integration.cj.config.CjProperties;
import com.garage.garageapi.integration.cj.dto.CjProductResponse;
import com.garage.garageapi.integration.cj.dto.CjProductVariantsResponse;
import com.garage.garageapi.integration.cj.dto.CjVariantInventoryResponse;
import com.garage.garageapi.integration.cj.dto.CjFreightResponse;
import com.garage.garageapi.integration.cj.dto.CjCreateOrderRequest;
import com.garage.garageapi.integration.cj.dto.CjCreateOrderResponse;
import com.garage.garageapi.integration.cj.dto.CjOrderLookupResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final String PRODUCT_VARIANT_QUERY =
            "/api2.0/v1/product/variant/query";

    private static final String PRODUCT_STOCK_QUERY_BY_VID =
            "/api2.0/v1/product/stock/queryByVid";

    private static final String FREIGHT_CALCULATE =
            "/api2.0/v1/logistic/freightCalculate";

    private static final String CREATE_ORDER_V2 =
            "/api2.0/v1/shopping/order/createOrderV2";

    private static final String GET_ORDER_DETAIL =
            "/api2.0/v1/shopping/order/getOrderDetail";

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
                    stringList(data, "productImageSet"),
                    text(data, "productKeyEn"),
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

        public CjProductVariantsResponse getProductVariants(
                        String accessToken,
                        String productId
        ) {
                try {
                        JsonNode response = restClient
                                        .get()
                                        .uri(builder -> builder
                                                        .path(PRODUCT_VARIANT_QUERY)
                                                        .queryParam("pid", productId)
                                                        .build()
                                        )
                                        .accept(MediaType.APPLICATION_JSON)
                                        .header("CJ-Access-Token", accessToken)
                                        .retrieve()
                                        .body(JsonNode.class);

                        JsonNode data = successfulData(response, "consulta de variantes");
                        if (!data.isArray()) {
                                throw new CjIntegrationException(
                                                "Resposta inválida da CJ durante consulta de variantes",
                                                CjIntegrationException.Reason.INVALID_RESPONSE
                                );
                        }

                        List<CjProductVariantsResponse.Variant> variants = new ArrayList<>();
                        for (JsonNode variant : data) {
                                variants.add(mapVariant(variant));
                        }

                        return new CjProductVariantsResponse(productId, List.copyOf(variants));
                } catch (RestClientResponseException exception) {
                        throw httpFailure("consulta de variantes", exception);
                } catch (RestClientException exception) {
                        throw new CjIntegrationException(
                                        "Falha temporária ao consultar variantes da CJ",
                                        exception,
                                        CjIntegrationException.Reason.UPSTREAM
                        );
                }
        }

    public CjVariantInventoryResponse getVariantInventory(String accessToken, String variantId) {
        try {
            JsonNode response = restClient.get()
                    .uri(builder -> builder.path(PRODUCT_STOCK_QUERY_BY_VID)
                            .queryParam("vid", variantId).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header("CJ-Access-Token", accessToken)
                    .retrieve().body(JsonNode.class);
            JsonNode data = successfulData(response, "consulta de disponibilidade");
            if (!data.isArray()) {
                throw new CjIntegrationException("Resposta inválida da CJ durante consulta de disponibilidade",
                        CjIntegrationException.Reason.INVALID_RESPONSE);
            }
            List<CjVariantInventoryResponse.Warehouse> warehouses = new ArrayList<>();
            for (JsonNode warehouse : data) {
                String returnedVid = text(warehouse, "vid");
                if (StringUtils.hasText(returnedVid) && !variantId.equals(returnedVid)) {
                    throw new CjIntegrationException("Resposta inválida da CJ durante consulta de disponibilidade",
                            CjIntegrationException.Reason.INVALID_RESPONSE);
                }
                warehouses.add(new CjVariantInventoryResponse.Warehouse(
                        text(warehouse, "areaId"), text(warehouse, "areaEn"),
                        requiredText(warehouse, "countryCode"),
                        requiredNonNegativeInteger(warehouse, "totalInventoryNum")));
            }
            return new CjVariantInventoryResponse(variantId, List.copyOf(warehouses));
        } catch (RestClientResponseException exception) {
            throw httpFailure("consulta de disponibilidade", exception);
        } catch (RestClientException exception) {
            throw new CjIntegrationException("Falha temporária ao consultar disponibilidade da CJ",
                    exception, CjIntegrationException.Reason.UPSTREAM);
        }
    }

    public CjFreightResponse calculateFreight(String accessToken, String originCountry,
                                              String destinationCountry, String zipCode,
                                              List<Map<String, Object>> products) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startCountryCode", originCountry);
        body.put("endCountryCode", destinationCountry);
        body.put("zip", zipCode);
        body.put("products", products);
        try {
            JsonNode response = restClient.post().uri(FREIGHT_CALCULATE)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .header("CJ-Access-Token", accessToken).body(body)
                    .retrieve().body(JsonNode.class);
            JsonNode data = successfulData(response, "cotação de frete");
            if (!data.isArray()) {
                throw new CjIntegrationException("Resposta inválida da CJ durante cotação de frete",
                        CjIntegrationException.Reason.INVALID_RESPONSE);
            }
            List<CjFreightResponse.Option> options = new ArrayList<>();
            for (JsonNode option : data) {
                options.add(new CjFreightResponse.Option(requiredText(option, "logisticName"),
                        requiredText(option, "logisticAging"), requiredDecimal(option, "logisticPrice"),
                        decimal(option, "taxesFee"), decimal(option, "clearanceOperationFee"),
                        decimal(option, "totalPostageFee")));
            }
            return new CjFreightResponse(List.copyOf(options));
        } catch (RestClientResponseException exception) {
            throw httpFailure("cotação de frete", exception);
        } catch (RestClientException exception) {
            throw new CjIntegrationException("Falha temporária ao calcular frete da CJ",
                    exception, CjIntegrationException.Reason.UPSTREAM);
        }
    }

    public CjCreateOrderResponse createOrder(String accessToken, CjCreateOrderRequest request) {
        try {
            JsonNode response = restClient.post().uri(CREATE_ORDER_V2)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .header("CJ-Access-Token", accessToken).body(request)
                    .retrieve().body(JsonNode.class);
            JsonNode data = createOrderData(response);
            String orderId = requiredText(data, "orderId");
            return new CjCreateOrderResponse(orderId, text(data, "shipmentOrderId"),
                    text(data, "orderNumber"), text(data, "orderStatus"));
        } catch (RestClientResponseException exception) {
            throw httpFailure("criação de pedido", exception);
        } catch (RestClientException exception) {
            throw new CjIntegrationException("Falha temporária ao criar pedido na CJ", exception,
                    CjIntegrationException.Reason.UPSTREAM);
        }
    }

    public Optional<CjOrderLookupResponse> findOrder(String accessToken, String orderNumber) {
        try {
            JsonNode response = restClient.get().uri(builder -> builder.path(GET_ORDER_DETAIL)
                            .queryParam("orderId", orderNumber).build())
                    .accept(MediaType.APPLICATION_JSON).header("CJ-Access-Token", accessToken)
                    .retrieve().body(JsonNode.class);
            if (isOrderNotFound(response)) return Optional.empty();
            JsonNode data = successfulData(response, "consulta de pedido");
            return Optional.of(new CjOrderLookupResponse(requiredText(data, "orderId"),
                    text(data, "shipmentOrderId"), requiredText(data, "orderNum"),
                    text(data, "orderStatus")));
        } catch (RestClientResponseException exception) {
            throw httpFailure("consulta de pedido", exception);
        } catch (RestClientException exception) {
            throw new CjIntegrationException("Falha temporária ao consultar pedido na CJ", exception,
                    CjIntegrationException.Reason.UPSTREAM);
        }
    }

    private JsonNode createOrderData(JsonNode response) {
        if (response != null && !response.path("result").asBoolean(false)) {
            String message = text(response, "message");
            String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("duplicate") || normalized.contains("already exist")
                    || normalized.contains("already been used")
                    || (normalized.contains("order") && normalized.contains("exist"))) {
                throw new CjIntegrationException("Referência de pedido já existente na CJ",
                        CjIntegrationException.Reason.CONFLICT);
            }
        }
        return successfulData(response, "criação de pedido");
    }

    private boolean isOrderNotFound(JsonNode response) {
        return response != null && !response.path("result").asBoolean(false)
                && response.path("code").asInt() == 1600300
                && "order not found".equalsIgnoreCase(text(response, "message"));
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
                List.of(),
                null,
                decimal(product, "sellPrice"),
                text(product, "categoryId"),
                text(product, "threeCategoryName"),
                text(product, "sku")
        );
    }

        private CjProductVariantsResponse.Variant mapVariant(JsonNode variant) {
                String variantKey = text(variant, "variantKey");
                Map<String, String> attributes = new LinkedHashMap<>();
                if (StringUtils.hasText(variantKey)) {
                        String[] options = variantKey.split("-", -1);
                        for (int index = 0; index < options.length; index++) {
                                if (StringUtils.hasText(options[index])) {
                                        attributes.put("option" + (index + 1), options[index].trim());
                                }
                        }
                }

                String name = text(variant, "variantNameEn");
                if (!StringUtils.hasText(name)) {
                        name = text(variant, "variantName");
                }

                return new CjProductVariantsResponse.Variant(
                                requiredText(variant, "vid"),
                                requiredText(variant, "pid"),
                                text(variant, "variantSku"),
                                name,
                                decimal(variant, "variantSellPrice"),
                                text(variant, "variantImage"),
                                variantKey,
                                Map.copyOf(attributes),
                                text(variant, "variantStandard"),
                                optionalInteger(variant, "variantLength"),
                                optionalInteger(variant, "variantWidth"),
                                optionalInteger(variant, "variantHeight"),
                                decimal(variant, "variantVolume"),
                                decimal(variant, "variantWeight")
                );
        }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        value.forEach(item -> {
            if (item.isString()) values.add(item.asString());
        });
        return List.copyOf(values);
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

        if (status == 409) {
            return new CjIntegrationException("Conflito retornado pela CJ", exception,
                    CjIntegrationException.Reason.CONFLICT);
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

    private BigDecimal requiredDecimal(JsonNode node, String field) {
        BigDecimal value = decimal(node, field);
        if (value == null || value.signum() < 0) {
            throw new CjIntegrationException("Valor inválido retornado pela CJ",
                    CjIntegrationException.Reason.INVALID_RESPONSE);
        }
        return value;
    }

    private int requiredNonNegativeInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.asLong() < 0 || value.asLong() > Integer.MAX_VALUE) {
            throw new CjIntegrationException("Estoque inválido retornado pela CJ",
                    CjIntegrationException.Reason.INVALID_RESPONSE);
        }
        return value.asInt();
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

        private Integer optionalInteger(JsonNode node, String field) {
                JsonNode value = node.path(field);
                return value.isNumber() ? value.asInt() : null;
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
