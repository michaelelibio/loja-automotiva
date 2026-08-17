package com.garage.garageapi.admin.stock;

import com.garage.garageapi.admin.stock.dto.AdminStockMovementPageResponse;
import com.garage.garageapi.admin.stock.dto.AdminStockMovementRequest;
import com.garage.garageapi.admin.stock.dto.AdminStockMovementResponse;
import com.garage.garageapi.admin.stock.dto.AdminStockSummaryResponse;
import com.garage.garageapi.stock.entity.StockMovementType;
import com.garage.garageapi.stock.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/admin/stock")
public class AdminStockController {
    private final StockService stockService;

    public AdminStockController(StockService stockService) { this.stockService = stockService; }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminStockMovementResponse create(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody AdminStockMovementRequest request) {
        return stockService.createManual(jwt, request);
    }

    @GetMapping("/movements")
    public AdminStockMovementPageResponse list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return stockService.list(productId, type, dateFrom, dateTo, page, size);
    }

    @GetMapping("/summary")
    public AdminStockSummaryResponse summary() { return stockService.summary(); }
}
