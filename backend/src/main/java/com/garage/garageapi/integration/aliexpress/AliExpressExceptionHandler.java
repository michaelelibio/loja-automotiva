package com.garage.garageapi.integration.aliexpress;

import com.garage.garageapi.shared.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataAccessException;
import java.time.Instant;
import java.util.Map;

@Order(0)
@RestControllerAdvice(assignableTypes={AliExpressAdminController.class,AliExpressCallbackController.class,AliExpressDiagnosticsController.class})
public class AliExpressExceptionHandler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AliExpressExceptionHandler.class);
    @ExceptionHandler(AliExpressIntegrationException.class)
    public ResponseEntity<ApiError> handle(AliExpressIntegrationException exception,HttpServletRequest request) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case AUTHORIZATION_REQUIRED, DUPLICATE, STALE_PREVIEW -> HttpStatus.CONFLICT;
            case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAVAILABLE, COUNTRY_REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        LOG.warn("AliExpress operation rejected; reason={}",exception.getReason());
        Map<String,String> fields = status == HttpStatus.CONFLICT ? Map.of("code",exception.getReason().name()) : Map.of();
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiError(Instant.now(),status.value(),status.getReasonPhrase(),exception.getMessage(),request.getRequestURI(),fields));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> persistence(DataAccessException exception,HttpServletRequest request) {
        return handle(new AliExpressIntegrationException(AliExpressIntegrationException.Reason.PERSISTENCE),request);
    }
}
