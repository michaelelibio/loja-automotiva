package com.garage.garageapi.auth.exception;

public class InvalidAccountTokenException extends RuntimeException {
    public InvalidAccountTokenException(String message) { super(message); }
}
