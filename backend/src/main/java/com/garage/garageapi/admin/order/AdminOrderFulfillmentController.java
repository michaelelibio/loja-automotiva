package com.garage.garageapi.admin.order;

import com.garage.garageapi.admin.order.dto.AdminOrderFulfillmentResponse;
import com.garage.garageapi.order.fulfillment.CjFulfillmentService;
import com.garage.garageapi.order.fulfillment.OrderFulfillmentRepository;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders/{orderId}/fulfillment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderFulfillmentController {
    private final OrderFulfillmentRepository repository;
    private final CjFulfillmentService fulfillmentService;

    public AdminOrderFulfillmentController(OrderFulfillmentRepository repository,
                                           CjFulfillmentService fulfillmentService) {
        this.repository = repository;
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping
    public AdminOrderFulfillmentResponse get(@PathVariable Long orderId) {
        return AdminOrderFulfillmentResponse.from(find(orderId));
    }

    @PostMapping("/retry")
    public AdminOrderFulfillmentResponse retry(@PathVariable Long orderId) {
        find(orderId);
        fulfillmentService.fulfill(orderId);
        return AdminOrderFulfillmentResponse.from(find(orderId));
    }

    private com.garage.garageapi.order.fulfillment.OrderFulfillment find(Long orderId) {
        return repository.findByOrderId(orderId).orElseThrow(() ->
                new ResourceNotFoundException("Fulfillment não encontrado para o pedido: " + orderId));
    }
}
