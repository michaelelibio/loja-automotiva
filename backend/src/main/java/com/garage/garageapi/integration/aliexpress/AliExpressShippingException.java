package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.shared.exception.ResourceConflictException;

/** Only local enum values/messages may cross the checkout boundary. No supplier payload/cause. */
public final class AliExpressShippingException extends ResourceConflictException {
    public enum Code {
        PERMISSION_DENIED, AUTHENTICATION_FAILED, INVALID_PARAMETERS, FREIGHT_UNAVAILABLE,
        INVALID_DELIVERY_METHOD, AMBIGUOUS_MONEY, INVALID_RESPONSE, EXTERNAL_FAILURE,
        NOT_CONFIGURED, RATE_LIMITED, DESTINATION_NOT_CONFIRMED
    }
    private final Code code;
    public AliExpressShippingException(Code code) {
        super(switch(code) {
            case PERMISSION_DENIED -> "A aplicacao AliExpress nao possui permissao para consultar este frete.";
            case AUTHENTICATION_FAILED -> "A autorizacao AliExpress nao esta disponivel ou foi rejeitada; nenhuma credencial foi renovada.";
            case INVALID_PARAMETERS -> "Produto, variante, quantidade ou parametros AliExpress invalidos para consultar frete.";
            case FREIGHT_UNAVAILABLE -> "O AliExpress nao retornou modalidades de frete disponiveis para esta consulta.";
            case INVALID_DELIVERY_METHOD -> "A modalidade de entrega retornada pelo AliExpress e invalida.";
            case AMBIGUOUS_MONEY -> "O valor do frete AliExpress possui unidade monetaria ambigua; nenhuma conversao foi presumida.";
            case INVALID_RESPONSE -> "A resposta AliExpress nao permite validar o frete.";
            case EXTERNAL_FAILURE -> "Nao foi possivel concluir a consulta de frete AliExpress.";
            case NOT_CONFIGURED -> "A integracao de frete AliExpress nao esta configurada.";
            case RATE_LIMITED -> "O limite de consultas de frete AliExpress foi atingido.";
            case DESTINATION_NOT_CONFIRMED -> "A consulta AliExpress retornou modalidades por pais, mas o frete ainda nao foi validado para o pedido e o endereco. A compra continua bloqueada.";
        });
        this.code=code;
    }
    public Code code() { return code; }
}
