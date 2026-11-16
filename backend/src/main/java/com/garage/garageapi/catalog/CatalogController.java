package com.garage.garageapi.catalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import java.util.List;
@RestController @Validated @RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service){this.service=service;}
    @GetMapping public CatalogService.Result browse(@Valid @ModelAttribute CatalogService.Query query){return service.browse(query);}
    @GetMapping("/categories") public List<CatalogService.CategoryView> categories(){return service.categoryList(false);}
    @GetMapping("/categories/resolve") public CatalogService.CategoryView resolve(@RequestParam @Size(max=250) String path){return service.resolve(path);}
}
