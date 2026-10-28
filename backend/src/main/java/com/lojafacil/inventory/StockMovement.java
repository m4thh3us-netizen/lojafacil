package com.lojafacil.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.lojafacil.catalog.ProductVariant;

/** Histórico imutável de cada alteração no saldo físico. */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor
public class StockMovement {

    public enum Type {
        /** Reposição / compra de fornecedor. */
        ENTRY,
        /** Saída manual (perda, uso interno, avaria). */
        EXIT,
        /** Ajuste de inventário (contagem), sempre com justificativa. */
        ADJUSTMENT,
        /** Baixa por venda confirmada. */
        SALE,
        /** Devolução recebida e em condições de revenda. */
        RETURN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** Positivo para entrada, negativo para saída. */
    private int quantity;

    private int balanceAfter;

    @Column(nullable = false)
    private String reason;

    private Long orderId;
    private Long supplierId;
    private Long userId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    StockMovement(ProductVariant variant, Type type, int quantity, String reason, Long orderId, Long supplierId, Long userId) {
        this.storeId = variant.getStoreId();
        this.variant = variant;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = variant.getPhysicalQty();
        this.reason = reason;
        this.orderId = orderId;
        this.supplierId = supplierId;
        this.userId = userId;
    }
}
