package com.garage.garageapi.shipping.service;

import com.garage.garageapi.favorite.exception.InactiveProductException;
import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.repository.ProductVariantRepository;
import com.garage.garageapi.product.entity.ProductVariant;
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
    private final ProductVariantRepository variantRepository;

    public ShippingService(ShippingProvider shippingProvider, ProductRepository productRepository,
                           ProductVariantRepository variantRepository) {
        this.shippingProvider = shippingProvider;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    public ShippingQuoteResponse quote(ShippingQuoteRequest request) {
        Map<ItemKey, Integer> lines = aggregate(request.items());
        Map<Long, Integer> quantities = aggregateProducts(lines);
        List<Product> products = new ArrayList<>(productRepository.findAllById(quantities.keySet()));
        products.sort(Comparator.comparing(Product::getId));
        validateProducts(products, quantities);
        Map<Long, Product> productsById = new TreeMap<>();
        products.forEach(product -> productsById.put(product.getId(), product));
        List<ShippingProvider.Item> items = lines.entrySet().stream().map(entry -> {
            Product product = productsById.get(entry.getKey().productId());
            ProductVariant variant = resolveVariant(product, entry.getKey().variantId());
            return item(product, variant, entry.getValue());
        }).toList();
        return new ShippingQuoteResponse(options(normalizeZipCode(request.zipCode()), items).stream()
                .map(ShippingOptionResponse::from).toList());
    }

    public ShippingProvider.Option select(String zipCode, List<ShippingProvider.Item> items,
                                          String requestedCode) {
        List<ShippingProvider.Option> quoted = options(normalizeZipCode(zipCode), items);
        if (requestedCode == null || requestedCode.isBlank()) return quoted.get(0);
        String code = normalizeShippingCode(requestedCode);
        return quoted.stream()
                .filter(option -> normalizeShippingCode(option.code()).equals(code))
                .findFirst()
                .orElseThrow(() -> new InvalidShippingOptionException(
                        "Opção de frete inválida: " + code));
    }

    public ShippingProvider.Item item(Product product, int quantity) {
        return item(product, null, quantity);
    }

    public ShippingProvider.Item item(Product product, ProductVariant variant, int quantity) {
        return new ShippingProvider.Item(product.getId(), variant == null ? null : variant.getId(),
                quantity, product.getPrice().setScale(2, RoundingMode.HALF_UP),
                product.getFulfillmentType(), product.getSupplier(),
                variant == null ? null : variant.getSupplierVariantId());
    }

    private List<ShippingProvider.Option> options(String zipCode, List<ShippingProvider.Item> items) {
        List<ShippingProvider.Option> options = shippingProvider.quote(
                new ShippingProvider.Request(zipCode, List.copyOf(items)));
        if (options == null || options.isEmpty()) {
            throw new InvalidShippingOptionException("Nenhuma opção de frete disponível");
        }
        return options;
    }

    private Map<ItemKey, Integer> aggregate(List<ShippingQuoteItemRequest> items) {
        Map<ItemKey, Integer> quantities = new TreeMap<>();
        for (ShippingQuoteItemRequest item : items) {
            quantities.merge(new ItemKey(item.productId(), item.variantId()),
                    item.quantity(), Math::addExact);
        }
        return quantities;
    }

    private Map<Long, Integer> aggregateProducts(Map<ItemKey, Integer> lines) {
        Map<Long, Integer> quantities = new TreeMap<>();
        lines.forEach((key, quantity) -> quantities.merge(key.productId(), quantity, Math::addExact));
        return quantities;
    }

    private ProductVariant resolveVariant(Product product, Long variantId) {
        boolean hasVariants = variantRepository.existsByProductId(product.getId());
        if (variantId == null) {
            if (hasVariants) throw new ResourceConflictException(
                    "Selecione uma variante para o produto " + product.getName() + ".");
            return null;
        }
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variante não encontrada: " + variantId));
        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new ResourceConflictException("A variante selecionada não pertence ao produto "
                    + product.getName() + ".");
        }
        if (!Boolean.TRUE.equals(variant.getActive())) {
            throw new ResourceConflictException("A variante selecionada não está disponível.");
        }
        return variant;
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

    private String normalizeShippingCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private record ItemKey(Long productId, Long variantId) implements Comparable<ItemKey> {
        @Override public int compareTo(ItemKey other) {
            int product = productId.compareTo(other.productId);
            if (product != 0) return product;
            if (variantId == null) return other.variantId == null ? 0 : -1;
            return other.variantId == null ? 1 : variantId.compareTo(other.variantId);
        }
    }
}
