package com.lojafacil.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.catalog.CatalogDtos.CategoryRequest;
import com.lojafacil.catalog.CatalogDtos.CategoryView;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.util.Slugs;

@Service
public class CategoryService {
    private final Category.Repo categories;

    public CategoryService(Category.Repo categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list(Long storeId) {
        return categories.findByStoreIdOrderByName(storeId).stream().map(CategoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listActive(Long storeId) {
        return categories.findByStoreIdAndActiveTrueOrderByName(storeId).stream().map(CategoryView::of).toList();
    }

    @Transactional
    public CategoryView create(Long storeId, CategoryRequest req) {
        String slug = Slugs.slugify(req.name());
        if (slug.isEmpty()) {
            throw Errors.badRequest("INVALID_NAME", "Nome de categoria inválido.");
        }
        if (categories.existsByStoreIdAndSlug(storeId, slug)) {
            throw Errors.conflict("CATEGORY_EXISTS", "Já existe uma categoria com este nome.");
        }
        Category c = new Category();
        c.setStoreId(storeId);
        c.setName(req.name().trim());
        c.setSlug(slug);
        c.setActive(req.active());
        return CategoryView.of(categories.save(c));
    }

    @Transactional
    public CategoryView update(Long storeId, Long id, CategoryRequest req) {
        Category c = find(storeId, id);
        String slug = Slugs.slugify(req.name());
        if (!slug.equals(c.getSlug()) && categories.existsByStoreIdAndSlug(storeId, slug)) {
            throw Errors.conflict("CATEGORY_EXISTS", "Já existe uma categoria com este nome.");
        }
        c.setName(req.name().trim());
        c.setSlug(slug);
        c.setActive(req.active());
        return CategoryView.of(c);
    }

    Category find(Long storeId, Long id) {
        return categories.findByIdAndStoreId(id, storeId).orElseThrow(() -> Errors.notFound("Categoria"));
    }
}
