package com.lojafacil.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Produto do catálogo. Os dados de venda (preço, custo) ficam aqui; o estoque fica nas variantes.
 * Um produto simples tem exatamente uma variante "padrão" com o mesmo SKU do produto.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private String description;

    @Column(nullable = false)
    private String sku;

    private String barcode;

    @Column(nullable = false)
    private BigDecimal costPrice;

    @Column(nullable = false)
    private BigDecimal salePrice;

    private BigDecimal promoPrice;

    @Column(nullable = false)
    private BigDecimal effectivePrice;

    private String unit = "UN";
    private int minStock;
    private String option1Name;
    private String option2Name;
    private boolean hasVariants;
    private boolean active = true;
    private boolean visible = true;
    private boolean featured;
    private Instant archivedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position, id")
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void beforeSave() {
        effectivePrice = promoPrice != null ? promoPrice : salePrice;
        updatedAt = Instant.now();
    }

    /** Pode ser vendido: ativo, não arquivado. (Visibilidade afeta apenas a vitrine.) */
    public boolean isSellable() {
        return active && archivedAt == null;
    }

    public boolean isOnStorefront() {
        return isSellable() && visible;
    }

    public String mainImagePath() {
        return images.isEmpty() ? null : images.get(0).getPath();
    }
}
