package com.lojafacil.catalog;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import com.lojafacil.shared.money.Money;

/**
 * Unidade vendável (SKU) com estoque próprio.
 *
 * <ul>
 *   <li><b>physicalQty</b>: unidades fisicamente na loja.</li>
 *   <li><b>reservedQty</b>: unidades separadas para pedidos aguardando pagamento.</li>
 *   <li><b>disponível</b> = físico - reservado (o que pode ser vendido agora).</li>
 * </ul>
 * Alterações de saldo acontecem apenas pelo {@code InventoryService}, sempre com a linha travada.
 */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private String sku;

    private String barcode;
    private String option1Value;
    private String option2Value;
    private BigDecimal priceOverride;
    private int physicalQty;
    private int reservedQty;
    private boolean active = true;

    @Version
    private long version;

    public int availableQty() {
        return physicalQty - reservedQty;
    }

    /** Ex.: "M / Azul". Nulo para a variante padrão de produto simples. */
    public String displayName() {
        if (option1Value == null && option2Value == null) {
            return null;
        }
        if (option2Value == null) {
            return option1Value;
        }
        return option1Value == null ? option2Value : option1Value + " / " + option2Value;
    }

    /** Preço de tabela da variante (sem promoção). */
    public BigDecimal listPrice() {
        return priceOverride != null ? priceOverride : product.getSalePrice();
    }

    /**
     * Desconto unitário. A promoção do produto vale para variantes sem preço próprio;
     * variantes com preço próprio não recebem o desconto promocional.
     */
    public BigDecimal unitDiscount() {
        if (priceOverride == null && product.getPromoPrice() != null) {
            return Money.normalize(product.getSalePrice().subtract(product.getPromoPrice()));
        }
        return Money.ZERO;
    }

    public BigDecimal finalUnitPrice() {
        return Money.normalize(listPrice().subtract(unitDiscount()));
    }

    public boolean isSellable() {
        return active && product.isSellable();
    }
}
