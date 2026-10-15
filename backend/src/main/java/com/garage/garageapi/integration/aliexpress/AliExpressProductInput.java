package com.garage.garageapi.integration.aliexpress;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

public final class AliExpressProductInput {
    private static final Set<String> HOSTS = Set.of("aliexpress.com", "www.aliexpress.com", "pt.aliexpress.com",
            "m.aliexpress.com", "es.aliexpress.com", "fr.aliexpress.com", "de.aliexpress.com", "it.aliexpress.com");
    private static final Pattern ID = Pattern.compile("[1-9][0-9]{5,19}");
    private AliExpressProductInput() {}
    public static String productId(String input) {
        if (input == null || input.length() > 2048) throw invalid();
        input = input.trim();
        if (ID.matcher(input).matches()) return input;
        try {
            URI uri = URI.create(input);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) throw invalid();
            var match = Pattern.compile("/item/([1-9][0-9]{5,19})\\.html/?").matcher(uri.getRawPath());
            if (!match.matches()) throw invalid();
            return match.group(1);
        } catch (IllegalArgumentException ignored) { throw invalid(); }
    }
    private static AliExpressIntegrationException invalid() { return new AliExpressIntegrationException(AliExpressIntegrationException.Reason.INVALID_INPUT); }
}
