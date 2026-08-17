package com.garage.garageapi.admin.finance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFinancePeriodException extends RuntimeException {
    public InvalidFinancePeriodException(String message) { super(message); }
}
