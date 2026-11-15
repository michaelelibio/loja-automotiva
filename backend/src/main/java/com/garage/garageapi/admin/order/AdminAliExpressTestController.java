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
    private final com.garage.garageapi.integration.aliexpress.AdminAliExpressTestSyncService sync;
    public AdminAliExpressTestController(AdminAliExpressTestService service,com.garage.garageapi.integration.aliexpress.AdminAliExpressTestSyncService sync) { this.service=service;this.sync=sync; }
    @GetMapping("/buyer-identity") public org.springframework.http.ResponseEntity<com.garage.garageapi.integration.aliexpress.AliExpressTokenService.BuyerIdentity> buyer(@PathVariable Long orderId) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.buyerIdentity(orderId));
    }
    @PostMapping("/preflight") public org.springframework.http.ResponseEntity<AdminAliExpressTestService.PayloadReview> preflight(@PathVariable Long orderId,Authentication actor) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.preflight(orderId,actor.getName()));
    }
    @GetMapping("/payload-review") public org.springframework.http.ResponseEntity<AdminAliExpressTestService.PayloadReview> review(@PathVariable Long orderId) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.reviewPayload(orderId));
    }
    @PostMapping("/sync") public AdminAliExpressTestService.View sync(@PathVariable Long orderId) { return sync.sync(orderId); }
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
