package com.garage.garageapi.integration.aliexpress;

public class AliExpressIntegrationException extends RuntimeException {
    public enum Reason {
        INVALID_INPUT, AUTHORIZATION_REQUIRED, NOT_CONFIGURED, NOT_FOUND, UNAVAILABLE,
        COUNTRY_REJECTED, PERMISSION, RATE_LIMIT, TIMEOUT, COMMUNICATION, INCOMPLETE,
        DUPLICATE, STALE_PREVIEW, PERSISTENCE
    }
    private final Reason reason;
    public AliExpressIntegrationException(Reason reason) {
        super(switch (reason) {
            case INVALID_INPUT -> "Entrada AliExpress invalida.";
            case AUTHORIZATION_REQUIRED -> "Autorize novamente a conta AliExpress pelo fluxo administrativo.";
            case NOT_CONFIGURED -> "Configuracao segura do AliExpress ausente ou invalida.";
            case NOT_FOUND -> "Produto AliExpress nao encontrado.";
            case UNAVAILABLE -> "Produto AliExpress indisponivel.";
            case COUNTRY_REJECTED -> "AliExpress rejeitou o pais de destino nesta consulta. Verifique produto, pais e permissao do app.";
            case PERMISSION -> "A aplicacao nao possui permissao para esta consulta AliExpress.";
            case RATE_LIMIT -> "Limite de consultas AliExpress atingido. Tente mais tarde.";
            case TIMEOUT -> "Tempo limite da consulta AliExpress excedido.";
            case COMMUNICATION -> "Falha de comunicacao com AliExpress.";
            case INCOMPLETE -> "Resposta AliExpress incompleta ou invalida; importacao cancelada.";
            case DUPLICATE -> "Produto ou SKU externo ja cadastrado; nenhuma informacao comercial foi sobrescrita.";
            case STALE_PREVIEW -> "Produto alterado desde a previa. Consulte e revise novamente.";
            case PERSISTENCE -> "Nao foi possivel persistir a importacao; nenhuma alteracao parcial foi confirmada.";
        });
        this.reason = reason;
    }
    public Reason getReason() { return reason; }
}
