package com.garage.garageapi.product.config;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.stock.service.StockService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.products.enabled", havingValue = "true", matchIfMissing = true)
public class ProductCatalogSeeder implements ApplicationRunner {
    private final ProductRepository productRepository;
    private final StockService stockService;

    public ProductCatalogSeeder(ProductRepository productRepository, StockService stockService) {
        this.productRepository = productRepository;
        this.stockService = stockService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        developmentCatalog().stream()
                .filter(product -> !productRepository.existsBySlug(product.slug()))
                .forEach(seed -> {
                    Product product = productRepository.saveAndFlush(seed.toEntity());
                    stockService.recordInitialStock(product, seed.stockQuantity(), null);
                });
    }

    private List<ProductSeed> developmentCatalog() {
        return List.of(
                new ProductSeed(
                        "Cera Automotiva Premium", "cera-automotiva-premium",
                        "Cera de alto brilho para proteção da pintura automotiva.",
                        "Cera automotiva premium desenvolvida para realçar o brilho e formar uma camada protetora contra sujeira, chuva e exposição cotidiana.",
                        "79.90", "94.90", "Estética Automotiva", 24),
                new ProductSeed(
                        "Shampoo Automotivo Neutro", "shampoo-automotivo-neutro",
                        "Shampoo concentrado de pH neutro para lavagem segura.",
                        "Remove sujeiras sem agredir a pintura, ceras ou selantes aplicados. Indicado para lavagens periódicas e uso com balde ou canhão de espuma.",
                        "39.90", null, "Limpeza", 40),
                new ProductSeed(
                        "Limpador de Interiores", "limpador-de-interiores",
                        "Limpador multiuso para painel, plásticos, tecidos e vinil.",
                        "Fórmula de baixa espuma para a limpeza cotidiana do interior do veículo, removendo poeira e marcas sem deixar acabamento oleoso.",
                        "34.90", "42.90", "Interior", 32),
                new ProductSeed(
                        "Pretinho para Pneus", "pretinho-para-pneus",
                        "Renovador de pneus com acabamento uniforme e duradouro.",
                        "Produto pronto para uso que recupera o aspecto escuro dos pneus e ajuda a protegê-los contra ressecamento causado pela exposição diária.",
                        "29.90", null, "Estética Automotiva", 36),
                new ProductSeed(
                        "Toalha de Microfibra Premium", "toalha-de-microfibra-premium",
                        "Toalha macia e absorvente para secagem e acabamento.",
                        "Microfibra de alta gramatura, indicada para secagem, remoção de ceras e acabamento sem riscar superfícies automotivas.",
                        "24.90", "29.90", "Limpeza", 60),
                new ProductSeed(
                        "Kit de Pincéis para Detalhamento", "kit-de-pinceis-para-detalhamento",
                        "Conjunto de pincéis para áreas internas e externas de difícil acesso.",
                        "Kit com diferentes tamanhos para limpeza de grades, emblemas, saídas de ar, comandos do painel e outros detalhes do veículo.",
                        "49.90", null, "Limpeza", 20),
                new ProductSeed(
                        "Lâmpada LED Automotiva", "lampada-led-automotiva",
                        "Lâmpada LED de luz branca para aplicação automotiva.",
                        "Solução de iluminação com baixo consumo e boa intensidade luminosa. Antes da compra, confira o encaixe compatível com o veículo.",
                        "119.90", "149.90", "Iluminação", 18),
                new ProductSeed(
                        "Suporte Magnético para Celular", "suporte-magnetico-para-celular",
                        "Suporte compacto para fixação do celular no painel.",
                        "Base magnética com ajuste de ângulo, projetada para manter o celular acessível durante o uso de navegação no veículo.",
                        "59.90", null, "Acessórios", 28),
                new ProductSeed(
                        "Aromatizante Automotivo", "aromatizante-automotivo",
                        "Aromatizante de fragrância suave para o interior do veículo.",
                        "Desenvolvido para proporcionar sensação agradável no habitáculo sem ocupar espaço no painel ou prejudicar a visibilidade.",
                        "19.90", null, "Interior", 50),
                new ProductSeed(
                        "Kit de Limpeza Automotiva", "kit-de-limpeza-automotiva",
                        "Kit essencial com produtos para lavagem e acabamento do veículo.",
                        "Conjunto para cuidados regulares com shampoo neutro, renovador de pneus e toalha de microfibra, reunidos em uma opção prática.",
                        "129.90", "159.90", "Estética Automotiva", 15)
        );
    }

    private record ProductSeed(
            String name,
            String slug,
            String description,
            String longDescription,
            String price,
            String oldPrice,
            String category,
            int stockQuantity
    ) {
        Product toEntity() {
            return new Product(name, slug, description, longDescription,
                    new BigDecimal(price), oldPrice == null ? null : new BigDecimal(oldPrice),
                    category, 0, null, true);
        }
    }
}
