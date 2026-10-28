package com.lojafacil.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lojafacil.catalog.CatalogDtos.ProductDetail;
import com.lojafacil.catalog.CatalogDtos.ProductListItem;
import com.lojafacil.catalog.CatalogDtos.ProductRequest;
import com.lojafacil.catalog.CatalogDtos.PublicProductCard;
import com.lojafacil.catalog.CatalogDtos.PublicProductDetail;
import com.lojafacil.catalog.CatalogDtos.VariantRequest;
import com.lojafacil.inventory.InventoryService;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.files.FileStorageService;
import com.lojafacil.shared.money.Money;
import com.lojafacil.shared.util.Slugs;
import com.lojafacil.shared.web.PageResponse;
import com.lojafacil.shared.web.Pages;

/**
 * Regras do catálogo.
 * <ul>
 *   <li>Produtos já vendidos nunca são excluídos: são arquivados (somem da vitrine e das buscas padrão,
 *       mas o histórico dos pedidos permanece intacto).</li>
 *   <li>Variantes removidas do formulário são desativadas, não apagadas, pelo mesmo motivo.</li>
 *   <li>O estoque não é editado aqui: o estoque inicial gera uma movimentação de entrada.</li>
 * </ul>
 */
@Service
public class ProductService {

    public static final int MAX_IMAGES = 8;

    private final ProductRepository products;
    private final VariantRepository variants;
    private final CategoryService categories;
    private final InventoryService inventory;
    private final FileStorageService files;
    private final AuditService audit;

    public ProductService(ProductRepository products, VariantRepository variants, CategoryService categories,
                          InventoryService inventory, FileStorageService files, AuditService audit) {
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.inventory = inventory;
        this.files = files;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ painel

    @Transactional(readOnly = true)
    public PageResponse<ProductListItem> search(Long storeId, String q, Long categoryId, String status,
                                                String sort, int page, int size) {
        var spec = ProductSpecs.all(ProductSpecs.ofStore(storeId), ProductSpecs.text(q),
                ProductSpecs.category(categoryId), ProductSpecs.status(status));
        Page<Product> result = products.findAll(spec, Pages.of(page, size, adminSort(sort)));
        return PageResponse.of(result, ProductListItem::of);
    }

    @Transactional(readOnly = true)
    public ProductDetail get(Long storeId, Long id) {
        Product p = find(storeId, id);
        return ProductDetail.of(p, products.isUsedInOrders(p.getId()));
    }

    @Transactional
    public ProductDetail create(Long storeId, ProductRequest req) {
        validatePrices(req);
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        if (products.existsByStoreIdAndSku(storeId, sku)) {
            throw Errors.conflict("SKU_IN_USE", "Já existe um produto com o SKU " + sku + ".");
        }
        Product p = new Product();
        p.setStoreId(storeId);
        p.setSlug(uniqueSlug(storeId, req.name()));
        p.setHasVariants(req.hasVariants());
        apply(storeId, p, req, sku);
        products.save(p);

        if (req.hasVariants()) {
            syncVariants(storeId, p, req);
        } else {
            ProductVariant def = new ProductVariant();
            def.setStoreId(storeId);
            def.setProduct(p);
            def.setSku(sku);
            def.setBarcode(trim(req.barcode()));
            p.getVariants().add(def);
            variants.save(def);
        }
        products.flush();
        registerInitialStock(storeId, p, req);
        audit.record(storeId, "PRODUCT", p.getId(), "PRODUCT_CREATED", p.getSku() + " - " + p.getName());
        return ProductDetail.of(p, false);
    }

    @Transactional
    public ProductDetail update(Long storeId, Long id, ProductRequest req) {
        validatePrices(req);
        Product p = find(storeId, id);
        if (req.hasVariants() != p.isHasVariants()) {
            throw Errors.rule("VARIANT_MODE_CHANGE",
                    "Não é possível alternar entre produto simples e com variações depois de criado. Cadastre um novo produto.");
        }
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        if (!sku.equals(p.getSku()) && products.existsByStoreIdAndSku(storeId, sku)) {
            throw Errors.conflict("SKU_IN_USE", "Já existe um produto com o SKU " + sku + ".");
        }
        BigDecimal oldPrice = p.getSalePrice();
        apply(storeId, p, req, sku);
        if (p.isHasVariants()) {
            syncVariants(storeId, p, req);
        } else {
            ProductVariant def = p.getVariants().get(0);
            if (!def.getSku().equals(sku) && variants.existsByStoreIdAndSku(storeId, sku)) {
                throw Errors.conflict("SKU_IN_USE", "SKU " + sku + " já usado por outra variante.");
            }
            def.setSku(sku);
            def.setBarcode(trim(req.barcode()));
        }
        audit.record(storeId, "PRODUCT", p.getId(), "PRODUCT_UPDATED",
                p.getSku() + (oldPrice.compareTo(p.getSalePrice()) != 0 ? " preço " + oldPrice + " -> " + p.getSalePrice() : ""));
        return ProductDetail.of(p, products.isUsedInOrders(p.getId()));
    }

    @Transactional
    public ProductDetail setArchived(Long storeId, Long id, boolean archived) {
        Product p = find(storeId, id);
        if (archived) {
            p.setArchivedAt(Instant.now());
            p.setActive(false);
            p.setVisible(false);
            p.setFeatured(false);
        } else {
            p.setArchivedAt(null);
        }
        audit.record(storeId, "PRODUCT", id, archived ? "PRODUCT_ARCHIVED" : "PRODUCT_UNARCHIVED", p.getSku());
        return ProductDetail.of(p, products.isUsedInOrders(id));
    }

    /** Exclusão definitiva só para produtos sem pedidos e sem movimentações; os demais devem ser arquivados. */
    @Transactional
    public void delete(Long storeId, Long id) {
        Product p = find(storeId, id);
        if (products.isUsedInOrders(id) || products.hasStockMovements(id)) {
            throw Errors.rule("PRODUCT_HAS_HISTORY",
                    "Este produto já possui pedidos ou movimentações de estoque. Arquive-o para preservar o histórico.");
        }
        p.getImages().forEach(i -> files.delete(i.getPath()));
        variants.deleteAll(p.getVariants());
        products.delete(p);
        audit.record(storeId, "PRODUCT", id, "PRODUCT_DELETED", p.getSku());
    }

    @Transactional
    public ProductDetail addImage(Long storeId, Long id, MultipartFile file) {
        Product p = find(storeId, id);
        if (p.getImages().size() >= MAX_IMAGES) {
            throw Errors.rule("TOO_MANY_IMAGES", "Cada produto pode ter no máximo " + MAX_IMAGES + " fotos.");
        }
        ProductImage img = new ProductImage();
        img.setProduct(p);
        img.setPath(files.storeImage(storeId, file, 1600));
        img.setPosition(p.getImages().size());
        p.getImages().add(img);
        products.flush();
        return ProductDetail.of(p, products.isUsedInOrders(id));
    }

    @Transactional
    public ProductDetail removeImage(Long storeId, Long id, Long imageId) {
        Product p = find(storeId, id);
        ProductImage img = p.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> Errors.notFound("Imagem"));
        p.getImages().remove(img);
        files.delete(img.getPath());
        renumber(p);
        return ProductDetail.of(p, products.isUsedInOrders(id));
    }

    /** Define a foto principal (posição 0). */
    @Transactional
    public ProductDetail setMainImage(Long storeId, Long id, Long imageId) {
        Product p = find(storeId, id);
        ProductImage img = p.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> Errors.notFound("Imagem"));
        p.getImages().remove(img);
        p.getImages().add(0, img);
        renumber(p);
        return ProductDetail.of(p, products.isUsedInOrders(id));
    }

    // ------------------------------------------------------------------ vitrine

    @Transactional(readOnly = true)
    public PageResponse<PublicProductCard> storefrontSearch(Long storeId, String q, String categorySlug,
                                                            BigDecimal minPrice, BigDecimal maxPrice, Boolean featured,
                                                            Boolean onSale, String sort, Pageable base) {
        var spec = ProductSpecs.all(ProductSpecs.ofStore(storeId), ProductSpecs.onStorefront(), ProductSpecs.text(q),
                ProductSpecs.categorySlug(categorySlug), ProductSpecs.minPrice(minPrice), ProductSpecs.maxPrice(maxPrice),
                ProductSpecs.featured(featured), ProductSpecs.onSale(onSale));
        Pageable pageable = Pages.of(base.getPageNumber(), base.getPageSize(), storefrontSort(sort));
        return PageResponse.of(products.findAll(spec, pageable), PublicProductCard::of);
    }

    @Transactional(readOnly = true)
    public PublicProductDetail storefrontProduct(Long storeId, String slug) {
        Product p = products.findByStoreIdAndSlug(storeId, slug)
                .filter(Product::isOnStorefront)
                .orElseThrow(() -> Errors.notFound("Produto"));
        return PublicProductDetail.of(p);
    }

    // ------------------------------------------------------------------ auxiliares

    Product find(Long storeId, Long id) {
        return products.findByIdAndStoreId(id, storeId).orElseThrow(() -> Errors.notFound("Produto"));
    }

    private void apply(Long storeId, Product p, ProductRequest req, String sku) {
        p.setName(req.name().trim());
        p.setDescription(trim(req.description()));
        p.setCategory(req.categoryId() == null ? null : categories.find(storeId, req.categoryId()));
        p.setSku(sku);
        p.setBarcode(trim(req.barcode()));
        p.setCostPrice(Money.normalize(req.costPrice()));
        p.setSalePrice(Money.normalize(req.salePrice()));
        p.setPromoPrice(req.promoPrice() == null ? null : Money.normalize(req.promoPrice()));
        p.setUnit(req.unit());
        p.setMinStock(req.minStock());
        p.setActive(req.active() && p.getArchivedAt() == null);
        p.setVisible(req.visible());
        p.setFeatured(req.featured());
        p.setOption1Name(req.hasVariants() ? trim(req.option1Name()) : null);
        p.setOption2Name(req.hasVariants() ? trim(req.option2Name()) : null);
        if (req.hasVariants() && p.getOption1Name() == null) {
            throw Errors.badRequest("OPTION_REQUIRED", "Informe o nome da variação (ex.: Tamanho).");
        }
    }

    /** Cria/atualiza variantes pelo id; variantes ausentes no formulário são desativadas. */
    private void syncVariants(Long storeId, Product p, ProductRequest req) {
        List<VariantRequest> incoming = req.variants() == null ? List.of() : req.variants();
        if (incoming.stream().noneMatch(VariantRequest::active)) {
            throw Errors.badRequest("VARIANTS_REQUIRED", "Cadastre ao menos uma variação ativa.");
        }
        Map<Long, ProductVariant> existing = p.getVariants().stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Set<String> combos = new HashSet<>();
        Set<String> skus = new HashSet<>();
        Set<Long> kept = new HashSet<>();
        for (VariantRequest vr : incoming) {
            if (vr.option1Value() == null || vr.option1Value().isBlank()) {
                throw Errors.badRequest("OPTION_VALUE_REQUIRED", "Informe o valor de \"" + p.getOption1Name() + "\" em todas as variações.");
            }
            if (vr.sku() == null || vr.sku().isBlank()) {
                throw Errors.badRequest("SKU_REQUIRED", "Informe o SKU de todas as variações.");
            }
            String vsku = vr.sku().trim().toUpperCase(Locale.ROOT);
            String combo = (vr.option1Value().trim() + "|" + (vr.option2Value() == null ? "" : vr.option2Value().trim())).toLowerCase(Locale.ROOT);
            if (!combos.add(combo)) {
                throw Errors.badRequest("DUPLICATE_VARIANT", "Há variações repetidas (" + vr.option1Value() + ").");
            }
            if (!skus.add(vsku)) {
                throw Errors.badRequest("DUPLICATE_SKU", "SKU repetido entre as variações: " + vsku);
            }
            ProductVariant v;
            if (vr.id() != null) {
                v = existing.get(vr.id());
                if (v == null) {
                    throw Errors.notFound("Variante");
                }
                if (!v.getSku().equals(vsku) && variants.existsByStoreIdAndSku(storeId, vsku)) {
                    throw Errors.conflict("SKU_IN_USE", "SKU " + vsku + " já está em uso.");
                }
                kept.add(v.getId());
            } else {
                if (variants.existsByStoreIdAndSku(storeId, vsku)) {
                    throw Errors.conflict("SKU_IN_USE", "SKU " + vsku + " já está em uso.");
                }
                v = new ProductVariant();
                v.setStoreId(storeId);
                v.setProduct(p);
                p.getVariants().add(v);
            }
            v.setSku(vsku);
            v.setBarcode(trim(vr.barcode()));
            v.setOption1Value(vr.option1Value().trim());
            v.setOption2Value(p.getOption2Name() == null ? null : trim(vr.option2Value()));
            v.setPriceOverride(vr.priceOverride() == null ? null : Money.normalize(vr.priceOverride()));
            v.setActive(vr.active());
            variants.save(v);
        }
        existing.values().stream().filter(v -> !kept.contains(v.getId())).forEach(v -> v.setActive(false));
    }

    private void registerInitialStock(Long storeId, Product p, ProductRequest req) {
        if (req.variants() == null) {
            return;
        }
        if (!p.isHasVariants()) {
            req.variants().stream().findFirst().map(VariantRequest::initialStock).filter(q -> q != null && q > 0)
                    .ifPresent(q -> inventory.entry(storeId, p.getVariants().get(0).getId(), q, "Estoque inicial", null));
            return;
        }
        for (VariantRequest vr : req.variants()) {
            if (vr.initialStock() != null && vr.initialStock() > 0) {
                String vsku = vr.sku().trim().toUpperCase(Locale.ROOT);
                p.getVariants().stream().filter(v -> v.getSku().equals(vsku)).findFirst()
                        .ifPresent(v -> inventory.entry(storeId, v.getId(), vr.initialStock(), "Estoque inicial", null));
            }
        }
    }

    private static void validatePrices(ProductRequest req) {
        if (req.promoPrice() != null && req.promoPrice().compareTo(req.salePrice()) >= 0) {
            throw Errors.badRequest("INVALID_PROMO", "O preço promocional deve ser menor que o preço de venda.");
        }
    }

    private String uniqueSlug(Long storeId, String name) {
        String base = Slugs.slugify(name);
        if (base.isEmpty()) {
            base = "produto";
        }
        String slug = base;
        int i = 2;
        while (products.existsByStoreIdAndSlug(storeId, slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    private static void renumber(Product p) {
        for (int i = 0; i < p.getImages().size(); i++) {
            p.getImages().get(i).setPosition(i);
        }
    }

    private static Sort adminSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "name" -> Sort.by("name");
            case "price_asc" -> Sort.by("effectivePrice").and(Sort.by("id"));
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "effectivePrice").and(Sort.by("id"));
            case "oldest" -> Sort.by("createdAt").and(Sort.by("id"));
            default -> Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }

    private static Sort storefrontSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "price_asc" -> Sort.by("effectivePrice").and(Sort.by("id"));
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "effectivePrice").and(Sort.by("id"));
            case "name" -> Sort.by("name");
            default -> Sort.by(Sort.Direction.DESC, "featured").and(Sort.by(Sort.Direction.DESC, "createdAt")).and(Sort.by("id"));
        };
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
