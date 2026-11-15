package com.garage.garageapi.admin.order;

import com.garage.garageapi.integration.aliexpress.AliExpressRecipientAdminService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/orders/{orderId}/aliexpress/recipient")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAliExpressRecipientController {
    private final AliExpressRecipientAdminService service;
    public AdminAliExpressRecipientController(AliExpressRecipientAdminService service) {this.service=service;}
    private ResponseEntity<AliExpressRecipientAdminService.View> response(AliExpressRecipientAdminService.View view) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(view);
    }
    @GetMapping public ResponseEntity<AliExpressRecipientAdminService.View> view(@PathVariable Long orderId) {return response(service.view(orderId));}
    @PatchMapping public ResponseEntity<AliExpressRecipientAdminService.View> correct(@PathVariable Long orderId,
            @Valid @RequestBody AliExpressRecipientAdminService.Correction request,@AuthenticationPrincipal Jwt actor) {
        return response(service.correct(orderId,request,actor.getSubject()));
    }
    @PostMapping("/reconcile") public ResponseEntity<AliExpressRecipientAdminService.View> reconcile(@PathVariable Long orderId,
            @Valid @RequestBody AliExpressRecipientAdminService.Reconciliation request,@AuthenticationPrincipal Jwt actor) {
        return response(service.reconcile(orderId,request,actor.getSubject()));
    }
}
