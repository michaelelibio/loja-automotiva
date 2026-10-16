package com.garage.garageapi.order.recipient;

import java.util.Map;

public class RecipientValidationException extends RuntimeException {
    private final Map<String,String> fields;
    public RecipientValidationException(Map<String,String> fields) {
        super("Confira os dados do destinatário para entrega ao Brasil.");
        this.fields=Map.copyOf(fields);
    }
    public Map<String,String> getFields() { return fields; }
}
