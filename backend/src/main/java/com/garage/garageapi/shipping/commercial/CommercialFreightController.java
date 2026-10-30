package com.garage.garageapi.shipping.commercial;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.List;
@RestController @RequestMapping("/api/admin/shipping-tables") @PreAuthorize("hasRole('ADMIN')")
public class CommercialFreightController {
    private final CommercialShippingService service;
    public CommercialFreightController(CommercialShippingService service){this.service=service;}
    @GetMapping public List<CommercialShippingService.View> list(){return service.list();}
    @PostMapping public CommercialShippingService.View draft(@Valid @RequestBody CommercialFreightPolicy.Document data){return service.draft(data);}
    @PostMapping("/bulk") public CommercialShippingService.View bulk(@RequestBody CommercialFreightPolicy.Document data){return service.bulkDraft(data);}
    @PostMapping("/{id}/publish") public CommercialShippingService.View publish(@PathVariable Long id){return service.publish(id);}
    @PostMapping("/{id}/retire") public CommercialShippingService.View retire(@PathVariable Long id){return service.retire(id);}
}
