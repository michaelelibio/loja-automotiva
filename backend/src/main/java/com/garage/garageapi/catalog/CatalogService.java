package com.garage.garageapi.catalog;

import com.garage.garageapi.product.entity.Product;
import com.garage.garageapi.product.dto.ProductResponse;
import com.garage.garageapi.product.repository.ProductRepository;
import com.garage.garageapi.product.service.ProductService;
import com.garage.garageapi.shared.exception.ResourceConflictException;
import com.garage.garageapi.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogService {
    private final ProductRepository products;
    private final CatalogCategoryRepository categories;
    private final ProductService productService;
    private final jakarta.persistence.EntityManager em;
    public CatalogService(ProductRepository products,CatalogCategoryRepository categories,ProductService productService,jakarta.persistence.EntityManager em) {
        this.products=products;this.categories=categories;this.productService=productService;this.em=em;
    }
    public record CategoryRequest(@NotBlank @Size(max=120) String name,
            @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=120) String slug,
            @Size(max=1500) String description,@Positive Long parentId,@Min(0) int position,boolean active) { }
    public record CategoryView(Long id,String name,String slug,String path,String description,Long parentId,int position,boolean active,long count) {
        static CategoryView from(CatalogCategory c,long count){return new CategoryView(c.getId(),c.getName(),c.getSlug(),c.getPath(),c.getDescription(),c.getParent()==null?null:c.getParent().getId(),c.getPosition(),c.isActive(),count);}
    }
    public record Bulk(@NotEmpty @Size(max=100) List<@NotNull @Positive Long> productIds,
            @NotEmpty Set<@NotBlank @Pattern(regexp="taxonomy|brand|surfaces|purposes|searchTerms|usage|concentrated|refill|featured|beginner|commercialOrigin") String> fields,
            @NotNull @Valid CatalogProductRequest catalog) { }
    public record Query(@Size(max=200) String q,@Size(max=250) String category,@Size(max=250) String subcategory,
            @Size(max=100) String legacyCategory,@Size(max=100) String brand,@Size(max=100) String surface,@Size(max=100) String purpose,
            @DecimalMin("0") BigDecimal minPrice,@DecimalMin("0") BigDecimal maxPrice,
            @Pattern(regexp="DOMESTIC|PROFESSIONAL|BOTH") String usage,@Pattern(regexp="NATIONAL|INTERNATIONAL") String origin,
            Boolean concentrated,Boolean refill,@Pattern(regexp="SINGLE|KIT") String type,
            @Pattern(regexp="featured|beginner|professional|newest|bestsellers") String section,
            @Pattern(regexp="relevance|lowest|highest|newest") String sort,@Min(0) @Max(1000000) Integer page,@Min(1) @Max(100) Integer size) { }
    public record Facets(List<CategoryView> categories,List<String> brands,List<String> surfaces,List<String> purposes,
            List<String> origins,List<String> usages,List<String> types,BigDecimal minPrice,BigDecimal maxPrice,
            boolean hasConcentrated,boolean hasRefill) { }
    public record Result(List<ProductResponse> content,int page,int size,long totalElements,int totalPages,Facets facets) { }

    @Transactional(readOnly=true)
    public List<CategoryView> categoryList(boolean admin) {
        var counts=new HashMap<Long,Long>();
        if(!admin)for(var p:products.findAll(productService.publicSelection())) {
            if(!p.isAvailableForSale())continue;
            if(p.getCatalogCategory()!=null&&p.getCatalogCategory().visible())counts.merge(p.getCatalogCategory().getId(),1L,Long::sum);
            if(p.getCatalogSubcategory()!=null&&p.getCatalogSubcategory().visible())counts.merge(p.getCatalogSubcategory().getId(),1L,Long::sum);
        }
        return categories.findAllByOrderByPositionAscIdAsc().stream().filter(c->admin||c.visible()&&counts.getOrDefault(c.getId(),0L)>0)
                .map(c->CategoryView.from(c,counts.getOrDefault(c.getId(),0L))).toList();
    }
    @Transactional(readOnly=true)
    public CategoryView resolve(String path) {
        var c=categories.findByPath(path).filter(CatalogCategory::visible).orElseThrow(()->new ResourceNotFoundException("Categoria não encontrada."));
        return CategoryView.from(c,0);
    }
    @Transactional
    public CategoryView saveCategory(Long id,CategoryRequest r) {
        var parent=r.parentId()==null?null:category(r.parentId());
        require(parent==null||parent.getParent()==null,"A hierarquia permite somente categoria e subcategoria.");
        if(id==null) {
            String path=parent==null?r.slug():parent.getPath()+"/"+r.slug();
            require(!categories.existsByPath(path),"URL de categoria já cadastrada.");
            return CategoryView.from(categories.saveAndFlush(new CatalogCategory(r.name(),r.slug(),parent,r.description(),r.position(),r.active())),0);
        }
        var c=category(id);
        require(c.getSlug().equals(r.slug())&&Objects.equals(c.getParent()==null?null:c.getParent().getId(),r.parentId()),"A URL e a categoria principal são estáveis e não podem ser alteradas.");
        c.update(r.name(),r.description(),r.position(),r.active());return CategoryView.from(c,0);
    }
    @Transactional
    public CatalogProductView classify(Long id,CatalogProductRequest r) {
        var p=products.findByIdWithLock(id).orElseThrow(()->new ResourceNotFoundException("Produto não encontrado."));
        apply(p,r);return CatalogProductView.from(p,true);
    }
    public void apply(Product p,CatalogProductRequest r) {
        if(r==null)return;
        var c=r.categoryId()==null?null:category(r.categoryId());var s=r.subcategoryId()==null?null:category(r.subcategoryId());
        require(c==null||c.getParent()==null,"Selecione uma categoria principal.");
        require(s==null||c!=null&&s.getParent()!=null&&s.getParent().getId().equals(c.getId()),"A subcategoria deve pertencer à categoria selecionada.");
        require(r.commercialOrigin()==null || ("NATIONAL".equals(r.commercialOrigin())&&"ALIEXPRESS".equalsIgnoreCase(p.getSupplier()))
                || ("INTERNATIONAL".equals(r.commercialOrigin())&&"CJ".equalsIgnoreCase(p.getSupplier())),
                "Nacional exige seleção AliExpress; Internacional exige seleção CJ. Deixe sem classificação quando não aplicável.");
        p.classify(c,s,r);
    }
    @Transactional
    public void bulk(Bulk r) {
        var ids=r.productIds().stream().distinct().sorted().toList();var selected=products.findAllByIdInOrderByIdWithLock(ids);
        require(selected.size()==ids.size(),"A seleção contém produtos inexistentes. Nenhuma classificação foi salva.");
        selected.forEach(p->{var v=CatalogProductView.from(p,true);var n=r.catalog();var f=r.fields();
            apply(p,new CatalogProductRequest(f.contains("taxonomy")?n.categoryId():v.category()==null?null:v.category().id(),
                f.contains("taxonomy")?n.subcategoryId():v.subcategory()==null?null:v.subcategory().id(),
                f.contains("brand")?n.brand():v.brand(),f.contains("surfaces")?n.surfaces():v.surfaces(),
                f.contains("purposes")?n.purposes():v.purposes(),f.contains("searchTerms")?n.searchTerms():v.searchTerms(),
                f.contains("usage")?n.usage():v.usage(),f.contains("concentrated")?n.concentrated():v.concentrated(),
                f.contains("refill")?n.refill():v.refill(),f.contains("featured")?n.featured():v.featured(),
                f.contains("beginner")?n.beginner():v.beginner(),f.contains("commercialOrigin")?n.commercialOrigin():v.commercialOrigin()));});
    }
    private CatalogCategory category(Long id){return categories.findById(id).orElseThrow(()->new ResourceNotFoundException("Categoria não encontrada."));}
    private static void require(boolean ok,String message){if(!ok)throw new ResourceConflictException(message);}
    private static String normalized(String text){return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();}
    private static Expression<String> normalized(Expression<String> value,CriteriaBuilder cb) {
        return cb.function("translate",String.class,cb.lower(cb.coalesce(value,"")),cb.literal("áàâãäéèêëíìîïóòôõöúùûüç"),cb.literal("aaaaaeeeeiiiiooooouuuuc"));
    }
    private static String like(String s){return "%"+normalized(s).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    private static boolean text(String s){return s!=null&&!s.isBlank();}

    @Transactional(readOnly=true)
    public Result browse(Query r) {
        require(r.minPrice()==null||r.maxPrice()==null||r.minPrice().compareTo(r.maxPrice())<=0,"Preço mínimo superior ao máximo.");
        Map<Long,Long> sales="bestsellers".equals(r.section())?confirmedSales():Map.of();
        Specification<Product> spec=productService.publicSelection();
        spec=spec.and((root,q,cb)->cb.or(cb.equal(root.get("fulfillmentType"),com.garage.garageapi.product.entity.FulfillmentType.DROPSHIPPING),cb.greaterThan(root.get("stockQuantity"),0)));
        if(text(r.q()))for(String term:normalized(r.q()).split("\\s+")) spec=spec.and((root,q,cb)->{
            var terms=q.subquery(Long.class);var correlated=terms.correlate(root);var word=correlated.<Product,String>joinSet("searchTerms");
            terms.select(correlated.get("id")).where(cb.like(normalized(word,cb),like(term),'\\'));
            return cb.or(cb.like(normalized(root.get("name"),cb),like(term),'\\'),cb.like(normalized(root.get("brand"),cb),like(term),'\\'),
                    cb.like(normalized(root.get("description"),cb),like(term),'\\'),cb.exists(terms));
        });
        for(var filter:List.of(new String[]{"catalogCategory",Objects.toString(r.category(),"")},new String[]{"catalogSubcategory",Objects.toString(r.subcategory(),"")}))if(text(filter[1])){
            var c=categories.findByPath(filter[1]).filter(CatalogCategory::visible).orElseThrow(()->new ResourceNotFoundException("Categoria não encontrada."));
            spec=spec.and((root,q,cb)->cb.equal(root.get(filter[0]).get("id"),c.getId()));
        }
        if(text(r.legacyCategory()))spec=spec.and((root,q,cb)->cb.equal(normalized(root.get("category"),cb),normalized(r.legacyCategory())));
        if(text(r.brand()))spec=spec.and((root,q,cb)->cb.equal(normalized(root.get("brand"),cb),normalized(r.brand())));
        if(text(r.surface()))spec=spec.and((root,q,cb)->cb.isMember(r.surface(),root.get("surfaces")));
        if(text(r.purpose()))spec=spec.and((root,q,cb)->cb.isMember(r.purpose(),root.get("purposes")));
        if(r.minPrice()!=null)spec=spec.and((root,q,cb)->cb.greaterThanOrEqualTo(root.get("price"),r.minPrice()));
        if(r.maxPrice()!=null)spec=spec.and((root,q,cb)->cb.lessThanOrEqualTo(root.get("price"),r.maxPrice()));
        if(text(r.origin()))spec=spec.and((root,q,cb)->cb.equal(root.get("commercialOrigin"),r.origin()));
        if(text(r.usage()))spec=spec.and((root,q,cb)->r.usage().equals("BOTH")?cb.equal(root.get("usage"),"BOTH"):root.get("usage").in(r.usage(),"BOTH"));
        if(r.concentrated()!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("concentrated"),r.concentrated()));
        if(r.refill()!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("refill"),r.refill()));
        if(text(r.type()))spec=spec.and((root,q,cb)->cb.equal(root.get("productType"),com.garage.garageapi.product.entity.ProductType.valueOf(r.type())));
        if("featured".equals(r.section()))spec=spec.and((root,q,cb)->cb.isTrue(root.get("featured")));
        if("beginner".equals(r.section()))spec=spec.and((root,q,cb)->cb.isTrue(root.get("beginner")));
        if("professional".equals(r.section()))spec=spec.and((root,q,cb)->root.get("usage").in("PROFESSIONAL","BOTH"));
        if("newest".equals(r.section()))spec=spec.and((root,q,cb)->cb.isNotNull(root.get("publishedAt")));
        if("bestsellers".equals(r.section()))spec=spec.and((root,q,cb)->sales.isEmpty()?cb.disjunction():root.get("id").in(sales.keySet()));
        String sort=r.sort()==null?"relevance":r.sort();
        var ordering=Sort.by(Sort.Order.desc("featured"),Sort.Order.asc("name"),Sort.Order.asc("id"));
        if(sort.equals("lowest"))ordering=Sort.by("price","id");
        if(sort.equals("highest"))ordering=Sort.by(Sort.Order.desc("price"),Sort.Order.asc("id"));
        if(sort.equals("newest")||"newest".equals(r.section())) {
            spec=spec.and((root,q,cb)->{if(q.getResultType()!=Long.class)q.orderBy(cb.asc(cb.selectCase().when(cb.isNull(root.get("publishedAt")),1).otherwise(0)),cb.desc(root.get("publishedAt")),cb.desc(root.get("id")));return cb.conjunction();});
            ordering=Sort.unsorted();
        }
        if(sort.equals("relevance")&&text(r.q())) {
            var search=normalized(r.q());
            spec=spec.and((root,q,cb)->{if(q.getResultType()!=Long.class)q.orderBy(cb.asc(cb.selectCase()
                .when(cb.equal(normalized(root.get("name"),cb),search),0)
                .when(cb.like(normalized(root.get("name"),cb),like(search).substring(1),'\\'),1)
                .when(cb.equal(normalized(root.get("brand"),cb),search),2).otherwise(3)),cb.desc(root.get("featured")),cb.asc(root.get("id")));return cb.conjunction();});
            ordering=Sort.unsorted();
        }
        int page=r.page()==null?0:r.page(),size=r.size()==null?24:r.size();
        // Facets reflect this complete selection, not just the displayed page.
        var matching=products.findAll(spec);
        List<ProductResponse> content;long count=matching.size();
        if("bestsellers".equals(r.section()))content=matching.stream().sorted(Comparator.<Product>comparingLong(p->sales.getOrDefault(p.getId(),0L)).reversed().thenComparing(Product::getId))
                .skip((long)page*size).limit(size).map(ProductResponse::from).toList();
        else content=products.findAll(spec,PageRequest.of(page,size,ordering)).map(ProductResponse::from).getContent();
        return new Result(content,page,size,count,(int)((count+size-1)/size),facets(matching));
    }
    private Facets facets(List<Product> ps) {
        var counts=new HashMap<Long,Long>();
        for(var p:ps){if(p.getCatalogCategory()!=null&&p.getCatalogCategory().visible())counts.merge(p.getCatalogCategory().getId(),1L,Long::sum);
            if(p.getCatalogSubcategory()!=null&&p.getCatalogSubcategory().visible())counts.merge(p.getCatalogSubcategory().getId(),1L,Long::sum);}
        var cs=categories.findAllByOrderByPositionAscIdAsc().stream().filter(c->c.visible()&&counts.containsKey(c.getId())).map(c->CategoryView.from(c,counts.get(c.getId()))).toList();
        return new Facets(cs,values(ps,Product::getBrand),ps.stream().flatMap(p->p.getSurfaces().stream()).distinct().sorted().toList(),
                ps.stream().flatMap(p->p.getPurposes().stream()).distinct().sorted().toList(),values(ps,Product::getCommercialOrigin),values(ps,Product::getUsage),
                values(ps,p->p.getProductType().name()),ps.stream().map(Product::getPrice).min(Comparator.naturalOrder()).orElse(null),
                ps.stream().map(Product::getPrice).max(Comparator.naturalOrder()).orElse(null),ps.stream().anyMatch(p->Boolean.TRUE.equals(p.getConcentrated())),ps.stream().anyMatch(p->Boolean.TRUE.equals(p.getRefill())));
    }
    private static List<String> values(List<Product> ps,Function<Product,String> f){return ps.stream().map(f).filter(CatalogService::text).distinct().sorted().toList();}
    private Map<Long,Long> confirmedSales() {
        var rows=em.createQuery("select i.productId,sum(i.quantity) from OrderItem i where i.order.status in ('PAID','PROCESSING','SHIPPED','DELIVERED') and i.order.paymentReviewRequired=false and exists (select p.id from Payment p where p.order=i.order and p.status='PAID' and p.paidAt is not null and p.providerLiveMode=true and p.providerVerifiedAt is not null) group by i.productId",Object[].class).getResultList();
        return rows.stream().collect(Collectors.toMap(r->(Long)r[0],r->((Number)r[1]).longValue()));
    }
}
