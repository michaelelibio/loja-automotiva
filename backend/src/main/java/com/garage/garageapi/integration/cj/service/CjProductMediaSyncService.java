package com.garage.garageapi.integration.cj.service;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.entity.ProductMedia;
import com.garage.garageapi.product.entity.ProductMediaSource;
import com.garage.garageapi.product.entity.ProductMediaType;
import com.garage.garageapi.product.repository.ProductMediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CjProductMediaSyncService {
    private final ProductMediaRepository mediaRepository;

    public CjProductMediaSyncService(ProductMediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public void syncImages(Product product, String bigImage, List<String> productImageSet) {
        List<String> desiredUrls = normalizedImages(bigImage, productImageSet);
        List<ProductMedia> current = mediaRepository
                .findAllByProductIdAndSourceOrderByPositionAscIdAsc(
                        product.getId(), ProductMediaSource.CJ);
        Map<String, ProductMedia> currentByUrl = new LinkedHashMap<>();
        current.forEach(media -> currentByUrl.putIfAbsent(media.getSourceUrl(), media));

        List<ProductMedia> changed = new ArrayList<>();
        for (int position = 0; position < desiredUrls.size(); position++) {
            String url = desiredUrls.get(position);
            ProductMedia media = currentByUrl.remove(url);
            if (media == null) {
                media = new ProductMedia(product, ProductMediaType.IMAGE, url, url, position,
                        product.getName(), ProductMediaSource.CJ);
            } else {
                media.update(url, url, position, product.getName(), true);
            }
            changed.add(media);
        }
        currentByUrl.values().forEach(media -> {
            media.setActive(false);
            changed.add(media);
        });
        mediaRepository.saveAll(changed);
    }

    static List<String> normalizedImages(String bigImage, List<String> productImageSet) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (productImageSet != null) {
            for (String url : productImageSet) addValid(unique, url);
        }
        String normalizedBigImage = validUrl(bigImage);
        if (normalizedBigImage != null && !unique.containsKey(normalizedBigImage)) {
            LinkedHashMap<String, String> withMainFirst = new LinkedHashMap<>();
            withMainFirst.put(normalizedBigImage, normalizedBigImage);
            withMainFirst.putAll(unique);
            unique = withMainFirst;
        }
        return List.copyOf(unique.values());
    }

    private static void addValid(Map<String, String> unique, String candidate) {
        String url = validUrl(candidate);
        if (url != null) unique.putIfAbsent(url, url);
    }

    private static String validUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) return null;
        String value = candidate.trim();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null
                    ? value : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
