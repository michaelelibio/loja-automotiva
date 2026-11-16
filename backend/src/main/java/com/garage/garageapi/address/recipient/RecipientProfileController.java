package com.garage.garageapi.address.recipient;

import com.garage.garageapi.order.dto.AliExpressRecipientRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class RecipientProfileController {
    private final RecipientProfileService service;
    public RecipientProfileController(RecipientProfileService service) { this.service=service; }
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");response.setHeader("Pragma","no-cache");response.setHeader("Vary","Authorization");
    }
    public record Save(@NotBlank @Size(max=150) String recipientName,@NotNull @Valid AliExpressRecipientRequest recipient) {
        @Override public String toString() { return "SaveRecipientProfile[REDACTED]"; }
    }
    @GetMapping("/api/addresses/{id}/recipient")
    public RecipientProfileService.View get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.get(jwt,id); }
    @PutMapping("/api/addresses/{id}/recipient") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Save request) { service.save(jwt,id,request.recipientName(),request.recipient()); }
    @DeleteMapping("/api/addresses/{id}/recipient") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { service.delete(jwt,id); }
    @GetMapping("/api/admin/addresses/{id}/recipient")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public RecipientProfileService.View admin(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) { return service.adminGet(jwt,id); }
}
