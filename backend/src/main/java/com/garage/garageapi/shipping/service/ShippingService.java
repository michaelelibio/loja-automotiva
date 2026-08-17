package com.garage.garageapi.shipping.service;

import com.garage.garageapi.favorite.exception.InactiveProductException;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import com.garage.garageapi.shipping.dto.ShippingOptionResponse;
import com.garage.garageapi.shipping.dto.ShippingQuoteItemRequest;
import com.garage.garageapi.shipping.dto.ShippingQuoteRequest;
import com.garage.garageapi.shipping.dto.ShippingQuoteResponse;
import com.garage.garageapi.shipping.exception.InvalidShippingOptionException;
import com.garage.garageapi.shipping.provider.ShippingProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ShippingService {
    public static final String DEFAULT_OPTION_CODE = "STANDARD";

    private final ShippingProvider shippingProvider;
    private final ProductRepository productRepository;

    public ShippingService(ShippingProvider shippingProvider, ProductRepository productRepository) {
        this.shippingProvider = shippingProvider;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ShippingQuoteResponse quote(ShippingQuoteRequest request) {
        Map<Long, Integer> quantities = aggregate(request.items());
        List<Product> products = new ArrayList<>(productRepository.findAllById(quantities.keySet()));
        products.sort(Comparator.comparing(Product::getId));
        validateProducts(products, quantities);
        List<ShippingProvider.Item> items = products.stream()
                .map(product -> item(product, quantities.get(product.getId())))
                .toList();
        return new ShippingQuoteResponse(options(normalizeZipCode(request.zipCode()), items).stream()
                .map(ShippingOptionResponse::from).toList());
    }

    public ShippingProvider.Option select(String zipCode, List<ShippingProvider.Item> items,
                                          String requestedCode) {
        String code = requestedCode == null || requestedCode.isBlank()
                ? DEFAULT_OPTION_CODE : requestedCode.trim().toUpperCase(Locale.ROOT);
        return options(normalizeZipCode(zipCode), items).stream()
                .filter(option -> option.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new InvalidShippingOptionException(
                        "Opção de frete inválida: " + code));
    }

    public ShippingProvider.Item item(Product product, int quantity) {
        return new ShippingProvider.Item(product.getId(), quantity,
                product.getPrice().setScale(2, RoundingMode.HALF_UP));
    }

    private List<ShippingProvider.Option> options(String zipCode, List<ShippingProvider.Item> items) {
        List<ShippingProvider.Option> options = shippingProvider.quote(
                new ShippingProvider.Request(zipCode, List.copyOf(items)));
        if (options == null || options.isEmpty()) {
            throw new InvalidShippingOptionException("Nenhuma opção de frete disponível");
        }
        return options;
    }

    private Map<Long, Integer> aggregate(List<ShippingQuoteItemRequest> items) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (ShippingQuoteItemRequest item : items) {
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        return quantities;
    }

    private void validateProducts(List<Product> products, Map<Long, Integer> quantities) {
        if (products.size() != quantities.size()) {
            Long missingId = quantities.keySet().stream()
                    .filter(id -> products.stream().noneMatch(product -> product.getId().equals(id)))
                    .findFirst().orElseThrow();
            throw new ResourceNotFoundException("Produto não encontrado: " + missingId);
        }
        for (Product product : products) {
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InactiveProductException(
                        "Produto inativo não pode ser incluído na cotação: " + product.getId());
            }
            if (!product.canFulfill(quantities.get(product.getId()))) {
                throw new ResourceConflictException(
                        "Estoque insuficiente para o produto " + product.getName() + ".");
            }
        }
    }

    private String normalizeZipCode(String zipCode) { return zipCode.replace("-", ""); }
}
