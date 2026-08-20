package com.garage.garageapi.order.fulfillment;

import com.garage.garageapi.integration.cj.dto.CjCreateOrderRequest;
import com.garage.garageapi.integration.cj.service.CjCommerceService;
import com.garage.garageapi.integration.cj.exception.CjIntegrationException;
import com.garage.garageapi.integration.cj.dto.CjOrderLookupResponse;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.order.entity.OrderItem;
import com.garage.garageapi.order.repository.OrderRepository;
import com.garage.garageapi.product.entity.FulfillmentType;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class CjFulfillmentService {
    private static final Logger log = LoggerFactory.getLogger(CjFulfillmentService.class);
    private final CjFulfillmentStateService stateService;
    private final OrderRepository orderRepository;
    private final OrderFulfillmentRepository fulfillmentRepository;
    private final CjCommerceService commerceService;

    public CjFulfillmentService(CjFulfillmentStateService stateService, OrderRepository orderRepository,
                                OrderFulfillmentRepository fulfillmentRepository,
                                CjCommerceService commerceService) {
        this.stateService = stateService;
        this.orderRepository = orderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.commerceService = commerceService;
    }

    public void fulfill(Long orderId) {
        CjFulfillmentStateService.Claim claim = stateService.claim(orderId);
        if (claim == null) return;
        try {
            OrderFulfillment fulfillment = fulfillmentRepository.findByOrderId(orderId).orElseThrow();
            Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
            CjCreateOrderRequest request = request(order, fulfillment.getExternalReference());
            if (reconcileClaimed(orderId, claim.token(), fulfillment.getExternalReference())) return;
            if (!stateService.markCreationAttempt(orderId, claim.token())) return;
            try {
                var response = commerceService.createOrder(request);
                stateService.complete(orderId, claim.token(), response.orderId(), response.shipmentOrderId());
                log.info("CJ fulfillment created; orderId={}; supplierOrderId={}", orderId, response.orderId());
            } catch (CjIntegrationException exception) {
                if (exception.getReason() != CjIntegrationException.Reason.CONFLICT
                        || !reconcileClaimed(orderId, claim.token(), fulfillment.getExternalReference())) {
                    throw exception;
                }
            }
        } catch (RuntimeException exception) {
            stateService.fail(orderId, claim.token(), safeMessage(exception));
            log.warn("CJ fulfillment failed; orderId={}; reason={}", orderId,
                    exception.getClass().getSimpleName());
        }
    }

    public ReconciliationResult reconcile(Long orderId) {
        OrderFulfillment current = fulfillmentRepository.findByOrderId(orderId).orElse(null);
        if (current == null || current.getStatus() == FulfillmentStatus.NOT_REQUIRED) {
            return ReconciliationResult.NOT_REQUIRED;
        }
        if (current.getStatus() == FulfillmentStatus.CREATED) return ReconciliationResult.FOUND;
        CjFulfillmentStateService.Claim claim = stateService.claim(orderId);
        if (claim == null) return ReconciliationResult.BUSY;
        try {
            boolean found = reconcileClaimed(orderId, claim.token(), current.getExternalReference());
            if (!found) stateService.release(orderId, claim.token(), claim.previousStatus());
            return found ? ReconciliationResult.FOUND : ReconciliationResult.NOT_FOUND;
        } catch (RuntimeException exception) {
            stateService.fail(orderId, claim.token(), "Falha temporária ao reconciliar pedido no fornecedor");
            throw exception;
        }
    }

    private boolean reconcileClaimed(Long orderId, String token, String externalReference) {
        Optional<CjOrderLookupResponse> result = commerceService.findOrder(externalReference);
        if (result.isEmpty()) {
            log.info("CJ fulfillment reconciliation not found; orderId={}; externalReference={}",
                    orderId, externalReference);
            return false;
        }
        var found = result.orElseThrow();
        if (!externalReference.equals(found.orderNumber())) {
            throw new CjIntegrationException("Referência divergente na resposta de reconciliação CJ",
                    CjIntegrationException.Reason.INVALID_RESPONSE);
        }
        stateService.complete(orderId, token, found.orderId(), found.shipmentOrderId());
        log.info("CJ fulfillment reconciled; orderId={}; externalReference={}; supplierOrderId={}",
                orderId, externalReference, found.orderId());
        return true;
    }

    public enum ReconciliationResult { FOUND, NOT_FOUND, NOT_REQUIRED, BUSY }

    private CjCreateOrderRequest request(Order order, String reference) {
        List<OrderItem> items = order.getItems().stream().filter(item ->
                item.getFulfillmentType() == FulfillmentType.DROPSHIPPING
                        && "CJ".equalsIgnoreCase(item.getSupplier())).toList();
        if (items.isEmpty()) throw invalid("Pedido não possui itens CJ");
        List<ShippingProvider.Leg> cjLegs = order.getShippingLegs().stream()
                .filter(leg -> "CJ".equalsIgnoreCase(leg.provider())).toList();
        if (cjLegs.size() != 1) throw invalid("Snapshot logístico CJ ausente ou ambíguo");
        ShippingProvider.Leg leg = cjLegs.get(0);
        require(leg.name(), "Método logístico CJ ausente");
        require(leg.originCountry(), "Origem logística CJ ausente");
        require(order.getRecipientName(), "Destinatário ausente");
        require(order.getZipCode(), "CEP ausente");
        require(order.getStreet(), "Endereço ausente");
        require(order.getNumber(), "Número do endereço ausente");
        require(order.getCity(), "Cidade ausente");
        require(order.getState(), "Estado ausente");

        List<CjCreateOrderRequest.Product> products = items.stream().map(item -> {
            require(item.getSupplierVariantId(), "Variante CJ ausente no snapshot");
            return new CjCreateOrderRequest.Product(item.getSupplierVariantId(), item.getQuantity(),
                    "order-item-" + item.getId());
        }).toList();
        String address2 = join(order.getNeighborhood(), order.getComplement());
        return new CjCreateOrderRequest(reference, order.getZipCode(), "Brazil", "BR",
                order.getState(), order.getCity(), order.getRecipientName(), order.getStreet(),
                address2, order.getNumber(), 3, leg.name(), leg.originCountry(), 1, products);
    }

    private String join(String first, String second) {
        if (!StringUtils.hasText(second)) return first;
        return first + " - " + second;
    }

    private void require(String value, String message) {
        if (!StringUtils.hasText(value)) throw invalid(message);
    }

    private IllegalStateException invalid(String message) { return new IllegalStateException(message); }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof IllegalStateException && StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        return "Falha temporária ao criar pedido no fornecedor";
    }
}
