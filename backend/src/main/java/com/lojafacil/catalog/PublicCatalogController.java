package com.lojafacil.catalog;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.catalog.CatalogDtos.CategoryView;
import com.lojafacil.catalog.CatalogDtos.PublicProductCard;
import com.lojafacil.catalog.CatalogDtos.PublicProductDetail;
import com.lojafacil.shared.web.PageResponse;
import com.lojafacil.store.StoreService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/public/stores/{slug}")
@Tag(name = "Vitrine pública")
public class PublicCatalogController {
    private final StoreService stores;
    private final ProductService products;
    private final CategoryService categories;

    public PublicCatalogController(StoreService stores, ProductService products, CategoryService categories) {
        this.stores = stores;
        this.products = products;
        this.categories = categories;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories(@PathVariable String slug) {
        return categories.listActive(stores.getBySlug(slug).getId());
    }

    @GetMapping("/products")
    public PageResponse<PublicProductCard> products(@PathVariable String slug,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String category,
                                                    @RequestParam(required = false) BigDecimal minPrice,
                                                    @RequestParam(required = false) BigDecimal maxPrice,
                                                    @RequestParam(required = false) Boolean featured,
                                                    @RequestParam(required = false) Boolean onSale,
                                                    @RequestParam(required = false) String sort,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "12") int size) {
        Long storeId = stores.getBySlug(slug).getId();
        return products.storefrontSearch(storeId, q, category, minPrice, maxPrice, featured, onSale, sort,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48)));
    }

    @GetMapping("/products/{productSlug}")
    public PublicProductDetail product(@PathVariable String slug, @PathVariable String productSlug) {
        return products.storefrontProduct(stores.getBySlug(slug).getId(), productSlug);
    }
}
