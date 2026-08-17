package com.garage.garageapi.admin.customer;

import com.garage.garageapi.admin.customer.dto.AdminCustomerDetailResponse;
import com.garage.garageapi.admin.customer.dto.AdminCustomerPageResponse;
import com.garage.garageapi.user.entity.AuthProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {
    private final AdminCustomerService customerService;

    public AdminCustomerController(AdminCustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public AdminCustomerPageResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean hasOrders,
            @RequestParam(required = false) AuthProvider authProvider,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return customerService.list(search, hasOrders, authProvider, page, size);
    }

    @GetMapping("/{id}")
    public AdminCustomerDetailResponse get(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int orderPage,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int orderSize) {
        return customerService.get(id, orderPage, orderSize);
    }
}
