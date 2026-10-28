package com.lojafacil.store;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Reserva o próximo número de pedido da loja de forma atômica.
     * O UPDATE trava a linha da loja, então dois pedidos simultâneos nunca recebem o mesmo número.
     */
    @Query(value = "UPDATE stores SET next_order_number = next_order_number + 1 WHERE id = :storeId RETURNING next_order_number - 1",
            nativeQuery = true)
    int nextOrderNumber(Long storeId);
}
