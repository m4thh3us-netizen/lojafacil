package com.lojafacil.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reserva de estoque para um pedido aguardando pagamento.
 * ACTIVE -> CONSUMED (pagamento aprovado: vira baixa) ou ACTIVE -> RELEASED (cancelado/expirado).
 */
@Entity
@Table(name = "stock_reservations")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation {

    public enum Status { ACTIVE, RELEASED, CONSUMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long variantId;

    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant createdAt = Instant.now();
    private Instant resolvedAt;

    StockReservation(Long storeId, Long orderId, Long variantId, int quantity, Instant expiresAt) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }
}
