package com.garage.garageapi.catalog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/admin/catalog")
public class AdminCatalogController {
    private final CatalogService service;
    public AdminCatalogController(CatalogService service){this.service=service;}
    @GetMapping("/categories") public List<CatalogService.CategoryView> categories(){return service.categoryList(true);}
    @PostMapping("/categories") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CatalogService.CategoryView create(@Valid @RequestBody CatalogService.CategoryRequest r){return service.saveCategory(null,r);}
    @PutMapping("/categories/{id}") public CatalogService.CategoryView update(@PathVariable Long id,@Valid @RequestBody CatalogService.CategoryRequest r){return service.saveCategory(id,r);}
    @PatchMapping("/products/{id}") public CatalogProductView classify(@PathVariable Long id,@Valid @RequestBody CatalogProductRequest r){return service.classify(id,r);}
    @PatchMapping("/products") public void bulk(@Valid @RequestBody CatalogService.Bulk r){service.bulk(r);}
}
