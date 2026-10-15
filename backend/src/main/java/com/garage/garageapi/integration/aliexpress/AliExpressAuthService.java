package com.garage.garageapi.integration.aliexpress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AliExpressAuthService {
    private static final Logger logger = LoggerFactory.getLogger(AliExpressAuthService.class);
    private static final String TOKEN_PATH = "/auth/token/create";
    private final RestClient client;
    private final String appKey;
    private final String appSecret;
    private final String callbackUrl;
    private final Clock clock;

    @Autowired
    public AliExpressAuthService(@Value("${aliexpress.app-key:}") String appKey,
                                @Value("${aliexpress.app-secret:}") String appSecret,
                                @Value("${aliexpress.callback-url:}") String callbackUrl) {
        this(defaultClient(), appKey, appSecret, callbackUrl, Clock.systemUTC());
    }

    AliExpressAuthService(RestClient client, String appKey, String appSecret,
                         String callbackUrl, Clock clock) {
        this.client = client;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.callbackUrl = callbackUrl;
        this.clock = clock;
    }

    public AliExpressTokenData exchangeCode(String code) {
        return tokenRequest(TOKEN_PATH, "code", code);
    }

    public AliExpressTokenData refreshToken(String refreshToken) {
        return tokenRequest("/auth/token/refresh", "refresh_token", refreshToken);
    }

    private AliExpressTokenData tokenRequest(String path, String parameterName, String credential) {
        try {
            requireConfiguration(credential);
            Map<String, String> parameters = new TreeMap<>();
            parameters.put("app_key", appKey);
            parameters.put(parameterName, credential);
            parameters.put("sign_method", "sha256");
            parameters.put("timestamp", Long.toString(clock.millis()));
            String signature = sign(parameters, path);
            parameters.put("sign", signature);
            StringBuilder form = new StringBuilder();
            parameters.forEach((key, value) -> {
                if (!form.isEmpty()) { form.append('&'); }
                form.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            });
            byte[] formBytes = form.toString().getBytes(StandardCharsets.UTF_8);
            // Secret is only the HMAC key, never a transmitted form field.
            JsonNode response = client.post().uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    // Streaming bypasses converter DEBUG logs that print form values.
                    .accept(MediaType.APPLICATION_JSON).body(output -> output.write(formBytes))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.isObject()) { throw new AliExpressAuthException(); }
            if (!"0".equals(text(response, "code"))) { throw new AliExpressAuthException(); }
            String access = text(response, "access_token");
            if (!StringUtils.hasText(access)) { throw new AliExpressAuthException(); }
            AliExpressTokenData tokens = new AliExpressTokenData(access, text(response, "refresh_token"),
                    number(response, "expires_in"), number(response, "refresh_expires_in"),
                    number(response, "expire_time"), number(response, "refresh_token_valid_time"),
                    text(response, "user_id"), text(response, "account_id"));
            logger.info("AliExpress token exchange succeeded; accessTokenPresent=true; refreshTokenPresent={}; expiresIn={}; refreshExpiresIn={}",
                    StringUtils.hasText(tokens.refreshToken()), tokens.expiresIn(), tokens.refreshExpiresIn());
            return tokens;
        } catch (Exception ignored) {
            // Upstream errors may echo credentials; never log their message/body/stack.
            logger.warn("AliExpress token exchange failed");
            throw new AliExpressAuthException();
        }
    }

    private String sign(Map<String, String> parameters, String path) throws Exception {
        StringBuilder input = new StringBuilder(path);
        parameters.forEach((key, value) -> input.append(key).append(value));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().withUpperCase().formatHex(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private void requireConfiguration(String code) {
        for (String value : new String[]{appKey, appSecret, callbackUrl, code}) {
            if (!StringUtils.hasText(value) || value.contains("COLE_AQUI")) {
                throw new AliExpressAuthException();
            }
        }
        URI callback = URI.create(callbackUrl);
        if (callback.getHost() == null || !("https".equals(callback.getScheme()) || "http".equals(callback.getScheme()))) {
            throw new AliExpressAuthException();
        }
    }

    private static String text(JsonNode response, String field) {
        JsonNode node = response.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static Long number(JsonNode response, String field) {
        String value = text(response, field);
        if (!StringUtils.hasText(value)) { return null; }
        long parsed = Long.parseLong(value);
        if (parsed < 0) { throw new AliExpressAuthException(); }
        return parsed;
    }

    private static RestClient defaultClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://api-sg.aliexpress.com/rest")
                .requestFactory(factory).build();
    }
}
