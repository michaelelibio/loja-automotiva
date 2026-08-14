package com.garage.garageapi.shared.exception;

import com.garage.garageapi.auth.exception.InvalidCredentialsException;
import com.garage.garageapi.auth.exception.InvalidGoogleTokenException;
import com.garage.garageapi.auth.exception.UserDisabledException;
import com.garage.garageapi.favorite.exception.InactiveProductException;
import com.garage.garageapi.payment.gateway.PaymentProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, InvalidGoogleTokenException.class})
    public ResponseEntity<ApiError> handleUnauthorized(RuntimeException exception,
                                                       HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(UserDisabledException exception,
                                                   HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception,
                                                    HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ResourceConflictException exception,
                                                    HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(InactiveProductException.class)
    public ResponseEntity<ApiError> handleInactiveProduct(InactiveProductException exception,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                      HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Dados inválidos", request.getRequestURI(), fields);
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ApiError> handlePaymentProvider(PaymentProviderException exception,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, exception.getMessage(), request.getRequestURI(), Map.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, String path,
                                            Map<String, String> fields) {
        ApiError error = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, path, fields
        );
        return ResponseEntity.status(status).body(error);
    }
}
