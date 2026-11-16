package com.garage.garageapi.catalog;
import com.garage.garageapi.product.entity.Product;
import java.time.Instant;
import java.util.List;
public record CatalogProductView(Category category,Category subcategory,String brand,List<String> surfaces,
        List<String> purposes,List<String> searchTerms,String usage,Boolean concentrated,Boolean refill,
        boolean featured,boolean beginner,String commercialOrigin,Instant publishedAt) {
    public record Category(Long id,String name,String slug,String path) {
        public static Category of(CatalogCategory c){return c==null?null:new Category(c.getId(),c.getName(),c.getSlug(),c.getPath());}
    }
    public static CatalogProductView from(Product p,boolean admin) {
        var c=p.getCatalogCategory();var s=p.getCatalogSubcategory();
        return new CatalogProductView(c!=null&&(admin||c.visible())?Category.of(c):null,
                s!=null&&(admin||s.visible())?Category.of(s):null,p.getBrand(),p.getSurfaces(),p.getPurposes(),p.getSearchTerms(),
                p.getUsage(),p.getConcentrated(),p.getRefill(),p.isFeatured(),p.isBeginner(),p.getCommercialOrigin(),p.getPublishedAt());
    }
}
