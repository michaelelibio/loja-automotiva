package com.garage.garageapi.admin.finance;

import com.garage.garageapi.admin.finance.dto.AdminFinanceResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/finance")
public class AdminFinanceController {
    private final AdminFinanceService financeService;

    public AdminFinanceController(AdminFinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping
    public AdminFinanceResponse get(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return financeService.get(dateFrom, dateTo);
    }
}
