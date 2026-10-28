package com.lojafacil.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.lojafacil.store.StoreDtos;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record CategoryRequest(@NotBlank(message = "Informe o nome.") @Size(max = 80) String name, boolean active) {
    }

    public record CategoryView(Long id, String name, String slug, boolean active) {
        static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getName(), c.getSlug(), c.isActive());
        }
    }

    public record VariantRequest(
            Long id,
            @Size(max = 60) @Pattern(regexp = "^$|^[A-Za-z0-9._-]+$", message = "SKU deve ter letras, números, ponto, hífen ou sublinhado.") String sku,
            @Size(max = 60) String barcode,
            @Size(max = 40) String option1Value,
            @Size(max = 40) String option2Value,
            @DecimalMin(value = "0.01", message = "Preço deve ser positivo.") @Digits(integer = 10, fraction = 2) BigDecimal priceOverride,
            @Min(value = 0, message = "Estoque inicial não pode ser negativo.") @Max(1_000_000) Integer initialStock,
            boolean active) {
    }

    public record ProductRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 160) String name,
            @Size(max = 4000) String description,
            Long categoryId,
            @NotBlank(message = "Informe o SKU.") @Size(max = 60)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "SKU deve ter letras, números, ponto, hífen ou sublinhado.") String sku,
            @Size(max = 60) String barcode,
            @NotNull(message = "Informe o preço de custo.") @DecimalMin(value = "0.00", message = "Custo não pode ser negativo.")
            @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @NotNull(message = "Informe o preço de venda.") @DecimalMin(value = "0.01", message = "Preço deve ser positivo.")
            @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
            @DecimalMin(value = "0.01", message = "Preço promocional deve ser positivo.") @Digits(integer = 10, fraction = 2) BigDecimal promoPrice,
            @NotBlank @Pattern(regexp = "^(UN|KG|G|L|ML|M|CX|PC|PAR|KIT)$", message = "Unidade inválida.") String unit,
            @Min(0) @Max(1_000_000) int minStock,
            boolean active,
            boolean visible,
            boolean featured,
            boolean hasVariants,
            @Size(max = 40) String option1Name,
            @Size(max = 40) String option2Name,

            @Valid @Size(max = 100) List<VariantRequest> variants) {
    }

    public record VariantView(Long id, String sku, String barcode, String option1Value, String option2Value, String name,
                              BigDecimal priceOverride, BigDecimal finalPrice, int physicalQty, int reservedQty,
                              int availableQty, boolean active, boolean lowStock) {
        static VariantView of(ProductVariant v) {
            return new VariantView(v.getId(), v.getSku(), v.getBarcode(), v.getOption1Value(), v.getOption2Value(),
                    v.displayName(), v.getPriceOverride(), v.finalUnitPrice(), v.getPhysicalQty(), v.getReservedQty(),
                    v.availableQty(), v.isActive(), v.availableQty() <= v.getProduct().getMinStock());
        }
    }

    public record ImageView(Long id, String url, int position) {
        static ImageView of(ProductImage i) {
            return new ImageView(i.getId(), StoreDtos.fileUrl(i.getPath()), i.getPosition());
        }
    }

    public record ProductListItem(Long id, String name, String sku, String categoryName, BigDecimal salePrice,
                                  BigDecimal promoPrice, BigDecimal costPrice, String imageUrl, boolean active,
                                  boolean visible, boolean featured, boolean archived, boolean hasVariants,
                                  int variantCount, int physicalQty, int reservedQty, int availableQty, boolean lowStock) {
        static ProductListItem of(Product p) {
            List<ProductVariant> vs = p.getVariants().stream().filter(ProductVariant::isActive).toList();
            int physical = vs.stream().mapToInt(ProductVariant::getPhysicalQty).sum();
            int reserved = vs.stream().mapToInt(ProductVariant::getReservedQty).sum();
            boolean low = vs.stream().anyMatch(v -> v.availableQty() <= p.getMinStock());
            return new ProductListItem(p.getId(), p.getName(), p.getSku(),
                    p.getCategory() != null ? p.getCategory().getName() : null,
                    p.getSalePrice(), p.getPromoPrice(), p.getCostPrice(), StoreDtos.fileUrl(p.mainImagePath()),
                    p.isActive(), p.isVisible(), p.isFeatured(), p.getArchivedAt() != null, p.isHasVariants(),
                    vs.size(), physical, reserved, physical - reserved, low);
        }
    }

    public record ProductDetail(Long id, String name, String slug, String description, Long categoryId, String sku,
                                String barcode, BigDecimal costPrice, BigDecimal salePrice, BigDecimal promoPrice,
                                String unit, int minStock, boolean active, boolean visible, boolean featured,
                                boolean hasVariants, String option1Name, String option2Name, Instant archivedAt,
                                List<VariantView> variants, List<ImageView> images, boolean usedInOrders) {
        static ProductDetail of(Product p, boolean usedInOrders) {
            return new ProductDetail(p.getId(), p.getName(), p.getSlug(), p.getDescription(),
                    p.getCategory() != null ? p.getCategory().getId() : null, p.getSku(), p.getBarcode(),
                    p.getCostPrice(), p.getSalePrice(), p.getPromoPrice(), p.getUnit(), p.getMinStock(),
                    p.isActive(), p.isVisible(), p.isFeatured(), p.isHasVariants(), p.getOption1Name(), p.getOption2Name(),
                    p.getArchivedAt(), p.getVariants().stream().map(VariantView::of).toList(),
                    p.getImages().stream().map(ImageView::of).toList(), usedInOrders);
        }
    }

    public record PublicProductCard(Long id, String slug, String name, String imageUrl, BigDecimal price,
                                    BigDecimal finalPrice, boolean onSale, boolean hasVariants, boolean available,
                                    String categorySlug) {
        static PublicProductCard of(Product p) {
            boolean available = p.getVariants().stream().anyMatch(v -> v.isActive() && v.availableQty() > 0);
            return new PublicProductCard(p.getId(), p.getSlug(), p.getName(), StoreDtos.fileUrl(p.mainImagePath()),
                    p.getSalePrice(), p.getEffectivePrice(), p.getPromoPrice() != null, p.isHasVariants(), available,
                    p.getCategory() != null ? p.getCategory().getSlug() : null);
        }
    }

    public record PublicVariant(Long id, String option1Value, String option2Value, String name, BigDecimal price,
                                BigDecimal finalPrice, boolean available, boolean lastUnits) {
        static PublicVariant of(ProductVariant v) {
            int avail = v.availableQty();
            return new PublicVariant(v.getId(), v.getOption1Value(), v.getOption2Value(), v.displayName(),
                    v.listPrice(), v.finalUnitPrice(), avail > 0, avail > 0 && avail <= 3);
        }
    }

    public record PublicProductDetail(Long id, String slug, String name, String description, String unit,
                                      List<String> images, BigDecimal price, BigDecimal finalPrice, boolean onSale,
                                      boolean hasVariants, String option1Name, String option2Name,
                                      List<PublicVariant> variants, CategoryView category) {
        static PublicProductDetail of(Product p) {
            return new PublicProductDetail(p.getId(), p.getSlug(), p.getName(), p.getDescription(), p.getUnit(),
                    p.getImages().stream().map(i -> StoreDtos.fileUrl(i.getPath())).toList(),
                    p.getSalePrice(), p.getEffectivePrice(), p.getPromoPrice() != null, p.isHasVariants(),
                    p.getOption1Name(), p.getOption2Name(),
                    p.getVariants().stream().filter(ProductVariant::isActive).map(PublicVariant::of).toList(),
                    p.getCategory() != null ? CategoryView.of(p.getCategory()) : null);
        }
    }
}
