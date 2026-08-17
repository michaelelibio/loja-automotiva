package com.garage.garageapi.auth.exception;

public class AccountEmailDeliveryException extends RuntimeException {
    public AccountEmailDeliveryException() {
        super("Não foi possível enviar o e-mail da conta");
    }
}
