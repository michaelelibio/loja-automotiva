package com.garage.garageapi.shared.json;

import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Checkout's JSON contract uses numbers; never coerce untrusted text into an amount. */
public final class StrictCheckoutAmountDeserializer extends ValueDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            return context.reportInputMismatch(BigDecimal.class,
                    "expectedTotal deve ser um número JSON.");
        }
        return parser.getDecimalValue();
    }
}
