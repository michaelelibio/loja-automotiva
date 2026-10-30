package com.garage.garageapi.order.quote;

import com.garage.garageapi.order.dto.OrderResponse;
import com.garage.garageapi.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class AssistedQuoteController {
    private final AssistedQuoteService service;
    private final UserService users;
    private final com.garage.garageapi.integration.aliexpress.AliExpressFreightService freight;
    public AssistedQuoteController(AssistedQuoteService service,UserService users,
            com.garage.garageapi.integration.aliexpress.AliExpressFreightService freight) {this.service=service;this.users=users;this.freight=freight;}
    @PostMapping("/api/admin/orders/{id}/quote/suggestions")
    public org.springframework.http.ResponseEntity<com.garage.garageapi.integration.aliexpress.AliExpressAssistedFreightPreview.Result> suggestions(@PathVariable Long id) {
        // The database transaction finishes before any external request; no quote or payment is modified.
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
            .body(freight.previewAssisted(service.freightContext(id)));
    }
    @GetMapping("/api/admin/orders/{id}/recipient-for-fulfillment")
    public org.springframework.http.ResponseEntity<AssistedQuoteService.Recipient> recipient(@PathVariable Long id) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.recipient(id));
    }
    @GetMapping("/api/admin/orders/{id}/quote")
    public AssistedQuote audit(@PathVariable Long id) { return service.audit(id); }
    @PostMapping("/api/admin/orders/{id}/quote")
    public OrderResponse confirm(@PathVariable Long id,@Valid @RequestBody AssistedQuoteService.Confirm request,@AuthenticationPrincipal Jwt jwt) {
        return OrderResponse.from(service.confirm(id,request,jwt.getSubject()));
    }
    @PostMapping("/api/orders/{id}/quote/accept")
    public OrderResponse accept(@PathVariable Long id,@Valid @RequestBody AssistedQuoteService.Accept request,@AuthenticationPrincipal Jwt jwt) {
        return OrderResponse.from(service.accept(id,users.findCurrentUser(jwt).getId(),request));
    }
    @PostMapping("/api/admin/orders/{id}/quote/recheck")
    public OrderResponse recheck(@PathVariable Long id,@Valid @RequestBody AssistedQuoteService.Recheck request,@AuthenticationPrincipal Jwt jwt) {return OrderResponse.from(service.recheck(id,request,jwt.getSubject()));}
    @PostMapping("/api/admin/orders/{id}/quote/refund-record")
    public OrderResponse refund(@PathVariable Long id,@Valid @RequestBody AssistedQuoteService.Reference request) {return OrderResponse.from(service.refund(id,request));}
    @PostMapping("/api/admin/orders/{id}/quote/manual-claim")
    public OrderResponse claim(@PathVariable Long id) {return OrderResponse.from(service.claimManual(id));}
    @PostMapping("/api/admin/orders/{id}/quote/manual-complete")
    public OrderResponse complete(@PathVariable Long id,@Valid @RequestBody AssistedQuoteService.ManualCompletion request) {return OrderResponse.from(service.completeManual(id,request));}
}
