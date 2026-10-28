package com.lojafacil.catalog;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros dinâmicos de produtos (Criteria API). Cada filtro é independente e todos geram
 * SQL parametrizado; o filtro por loja é sempre aplicado primeiro.
 */
final class ProductSpecs {

    private ProductSpecs() {
    }

    static Specification<Product> ofStore(Long storeId) {
        return (root, q, cb) -> cb.equal(root.get("storeId"), storeId);
    }

    static Specification<Product> text(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("sku")), like),
                cb.like(cb.lower(cb.coalesce(root.get("barcode"), "")), like));
    }

    static Specification<Product> category(Long categoryId) {
        return categoryId == null ? null : (root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    static Specification<Product> categorySlug(String slug) {
        return slug == null || slug.isBlank() ? null : (root, q, cb) -> cb.equal(root.join("category").get("slug"), slug);
    }

    /** status: "active" (ativos), "inactive" (inativos), "archived" (arquivados), "all" ou nulo (não arquivados). */
    static Specification<Product> status(String status) {
        if ("archived".equals(status)) {
            return (root, q, cb) -> cb.isNotNull(root.get("archivedAt"));
        }
        if ("all".equals(status)) {
            return null;
        }
        Specification<Product> notArchived = (root, q, cb) -> cb.isNull(root.get("archivedAt"));
        if ("active".equals(status)) {
            return notArchived.and((root, q, cb) -> cb.isTrue(root.get("active")));
        }
        if ("inactive".equals(status)) {
            return notArchived.and((root, q, cb) -> cb.isFalse(root.get("active")));
        }
        return notArchived;
    }

    static Specification<Product> onStorefront() {
        return (root, q, cb) -> cb.and(cb.isTrue(root.get("active")), cb.isTrue(root.get("visible")), cb.isNull(root.get("archivedAt")));
    }

    static Specification<Product> featured(Boolean featured) {
        return Boolean.TRUE.equals(featured) ? (root, q, cb) -> cb.isTrue(root.get("featured")) : null;
    }

    static Specification<Product> minPrice(BigDecimal min) {
        return min == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("effectivePrice"), min);
    }

    static Specification<Product> maxPrice(BigDecimal max) {
        return max == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("effectivePrice"), max);
    }

    /** Combina os filtros com AND, ignorando os que não foram informados (null). */
    @SafeVarargs
    static Specification<Product> all(Specification<Product> first, Specification<Product>... others) {
        Specification<Product> result = first;
        for (Specification<Product> s : others) {
            if (s != null) {
                result = result.and(s);
            }
        }
        return result;
    }

    static Specification<Product> onSale(Boolean onSale) {
        return Boolean.TRUE.equals(onSale) ? (root, q, cb) -> cb.isNotNull(root.get("promoPrice")) : null;
    }
}
