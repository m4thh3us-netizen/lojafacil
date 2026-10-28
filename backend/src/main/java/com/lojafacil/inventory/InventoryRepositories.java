package com.lojafacil.inventory;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repositórios do módulo de estoque. */
public final class InventoryRepositories {

    private InventoryRepositories() {
    }

    public interface MovementRepo extends JpaRepository<StockMovement, Long> {

        @EntityGraph(attributePaths = {"variant", "variant.product"})
        @Query("""
                select m from StockMovement m
                where m.storeId = :storeId
                  and (:variantId is null or m.variant.id = :variantId)
                  and (:productId is null or m.variant.product.id = :productId)
                  and (:type is null or m.type = :type)
                  and m.createdAt >= :from and m.createdAt < :to
                order by m.createdAt desc, m.id desc
                """)
        Page<StockMovement> search(Long storeId, Long variantId, Long productId, StockMovement.Type type,
                                   Instant from, Instant to, Pageable pageable);
    }

    public interface ReservationRepo extends JpaRepository<StockReservation, Long> {

        List<StockReservation> findByOrderIdAndStatus(Long orderId, StockReservation.Status status);

        /** Pedidos com reservas vencidas (o job de expiração processa em lotes). */
        @Query("select distinct r.orderId from StockReservation r where r.status = 'ACTIVE' and r.expiresAt < :now")
        List<Long> findOrdersWithExpiredReservations(Instant now, Pageable pageable);
    }
}
