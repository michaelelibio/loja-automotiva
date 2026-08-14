package com.garage.garageapi.admin.order;

import com.garage.garageapi.admin.order.dto.AdminOrderPageResponse;
import com.garage.garageapi.admin.order.dto.AdminOrderResponse;
import com.garage.garageapi.admin.order.dto.AdminOrderStatusUpdateRequest;
import com.garage.garageapi.order.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public AdminOrderPageResponse list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminOrderService.list(status, page, size);
    }

    @GetMapping("/{id}")
    public AdminOrderResponse get(@PathVariable Long id) {
        return adminOrderService.get(id);
    }

    @PatchMapping("/{id}/status")
    public AdminOrderResponse transition(@PathVariable Long id,
                                         @Valid @RequestBody AdminOrderStatusUpdateRequest request) {
        return adminOrderService.transition(id, request.status());
    }
}
