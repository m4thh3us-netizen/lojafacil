package com.lojafacil.catalog;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lojafacil.catalog.CatalogDtos.CategoryRequest;
import com.lojafacil.catalog.CatalogDtos.CategoryView;
import com.lojafacil.catalog.CatalogDtos.ProductDetail;
import com.lojafacil.catalog.CatalogDtos.ProductListItem;
import com.lojafacil.catalog.CatalogDtos.ProductRequest;
import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.web.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Catálogo no painel. A loja sempre vem da sessão ({@link CurrentUser}), nunca da requisição. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize(Access.OPERATIONS)
@Tag(name = "Catálogo")
public class CatalogController {

    private final ProductService products;
    private final CategoryService categories;
    private final CurrentUser currentUser;

    public CatalogController(ProductService products, CategoryService categories, CurrentUser currentUser) {
        this.products = products;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return categories.list(currentUser.storeId());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest req) {
        return categories.create(currentUser.storeId(), req);
    }

    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return categories.update(currentUser.storeId(), id, req);
    }

    @GetMapping("/products")
    public PageResponse<ProductListItem> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return products.search(currentUser.storeId(), q, categoryId, status, sort, page, size);
    }

    @GetMapping("/products/{id}")
    public ProductDetail get(@PathVariable Long id) {
        return products.get(currentUser.storeId(), id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetail create(@Valid @RequestBody ProductRequest req) {
        return products.create(currentUser.storeId(), req);
    }

    @PutMapping("/products/{id}")
    public ProductDetail update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return products.update(currentUser.storeId(), id, req);
    }

    @PostMapping("/products/{id}/archive")
    public ProductDetail archive(@PathVariable Long id) {
        return products.setArchived(currentUser.storeId(), id, true);
    }

    @PostMapping("/products/{id}/unarchive")
    public ProductDetail unarchive(@PathVariable Long id) {
        return products.setArchived(currentUser.storeId(), id, false);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        products.delete(currentUser.storeId(), id);
    }

    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDetail addImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return products.addImage(currentUser.storeId(), id, file);
    }

    @DeleteMapping("/products/{id}/images/{imageId}")
    public ProductDetail removeImage(@PathVariable Long id, @PathVariable Long imageId) {
        return products.removeImage(currentUser.storeId(), id, imageId);
    }

    @PostMapping("/products/{id}/images/{imageId}/main")
    public ProductDetail setMainImage(@PathVariable Long id, @PathVariable Long imageId) {
        return products.setMainImage(currentUser.storeId(), id, imageId);
    }
}
