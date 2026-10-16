package com.garage.garageapi.admin.order;

import com.garage.garageapi.integration.aliexpress.AliExpressOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders/{orderId}/aliexpress")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAliExpressOrderController {
    private final AliExpressOrderService service;
    public AdminAliExpressOrderController(AliExpressOrderService service) { this.service=service; }
    @GetMapping public AliExpressOrderService.View get(@PathVariable Long orderId) { return service.view(orderId); }
    @PostMapping("/send") public AliExpressOrderService.View send(@PathVariable Long orderId) { return service.send(orderId); }
}
