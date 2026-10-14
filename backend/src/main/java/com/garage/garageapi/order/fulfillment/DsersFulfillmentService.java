package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.integration.dsers.DsersOrderGateway;
import com.garage.garageapi.integration.dsers.DsersOrderSubmission;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.product.entity.FulfillmentType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DsersFulfillmentService {
    private final FulfillmentStateService stateService;
    private final OrderRepository orderRepository;
    private final OrderFulfillmentRepository fulfillmentRepository;
    private final DsersOrderGateway gateway;

    public DsersFulfillmentService(FulfillmentStateService stateService, OrderRepository orderRepository,
                                   OrderFulfillmentRepository fulfillmentRepository,
                                   DsersOrderGateway gateway) {
        this.stateService = stateService;
        this.orderRepository = orderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.gateway = gateway;
    }

    public void fulfill(Long orderId) {
        FulfillmentStateService.Claim claim = stateService.claim(orderId);
        if (claim == null) return;
        try {
            OrderFulfillment fulfillment = fulfillmentRepository.findByOrderId(orderId).orElseThrow();
            if (!"DSERS".equals(fulfillment.getProvider())) throw invalid("Fulfillment não pertence ao DSers");
            Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
            DsersOrderSubmission submission = submission(order, fulfillment.getExternalReference());
            var existing = gateway.findByExternalReference(fulfillment.getExternalReference());
            if (existing.isPresent()) {
                complete(orderId, claim.token(), existing.orElseThrow());
                return;
            }
            if (!stateService.markCreationAttempt(orderId, claim.token())) return;
            complete(orderId, claim.token(), gateway.submit(submission));
        } catch (RuntimeException exception) {
            stateService.fail(orderId, claim.token(), safeMessage(exception));
        }
    }

    private void complete(Long orderId, String token, DsersOrderGateway.Result result) {
        stateService.complete(orderId, token, result.supplierOrderId(), result.supplierShipmentOrderId());
    }

    private DsersOrderSubmission submission(Order order, String reference) {
        List<OrderItem> items = order.getItems().stream().filter(item ->
                item.getFulfillmentType() == FulfillmentType.DROPSHIPPING
                        && "DSERS".equalsIgnoreCase(item.getSupplier())).toList();
        if (items.isEmpty()) throw invalid("Pedido não possui itens DSers");
        List<DsersOrderSubmission.Item> mapped = items.stream().map(item -> {
            require(item.getSupplierProductId(), "Produto DSers ausente no snapshot");
            require(item.getSupplierVariantId(), "Variante DSers ausente no snapshot");
            return new DsersOrderSubmission.Item(item.getSupplierProductId(),
                    item.getSupplierVariantId(), item.getSupplierSku(), item.getQuantity(),
                    "order-item-" + item.getId());
        }).toList();
        require(order.getRecipientName(), "Destinatário ausente");
        require(order.getZipCode(), "CEP ausente");
        require(order.getStreet(), "Endereço ausente");
        require(order.getNumber(), "Número do endereço ausente");
        require(order.getCity(), "Cidade ausente");
        require(order.getState(), "Estado ausente");
        var recipient = new DsersOrderSubmission.Recipient(order.getRecipientName(), order.getZipCode(),
                order.getStreet(), order.getNumber(), order.getComplement(), order.getNeighborhood(),
                order.getCity(), order.getState(), "BR");
        return new DsersOrderSubmission(reference, recipient, mapped);
    }

    private void require(String value, String message) {
        if (!StringUtils.hasText(value)) throw invalid(message);
    }

    private IllegalStateException invalid(String message) { return new IllegalStateException(message); }
    private String safeMessage(RuntimeException exception) {
        return exception instanceof IllegalStateException && StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : "Falha temporária ao processar pedido DSers";
    }
}
