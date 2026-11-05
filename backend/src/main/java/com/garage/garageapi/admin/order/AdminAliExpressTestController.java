package com.garage.garageapi.admin.order;

import com.garage.garageapi.integration.aliexpress.AdminAliExpressTestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders/{orderId}/aliexpress-tests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAliExpressTestController {
    private final AdminAliExpressTestService service;
    public AdminAliExpressTestController(AdminAliExpressTestService service) { this.service=service; }
    @GetMapping public AdminAliExpressTestService.View get(@PathVariable Long orderId) { return service.view(orderId); }
    @PostMapping("/simulate-payment") public AdminAliExpressTestService.View payment(@PathVariable Long orderId, Authentication actor) {
        return service.simulate(orderId,actor.getName(),false);
    }
    @PostMapping("/simulate-supplier") public AdminAliExpressTestService.View supplier(@PathVariable Long orderId, Authentication actor) {
        return service.simulate(orderId,actor.getName(),true);
    }
    @PostMapping("/create-real") public AdminAliExpressTestService.View real(@PathVariable Long orderId, Authentication actor,
            @RequestBody AdminAliExpressTestService.Confirmation confirmation) { return service.createReal(orderId,actor.getName(),confirmation); }
}
