package com.lojafacil.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndSku(Long storeId, String sku);

    /**
     * Trava (SELECT ... FOR UPDATE) as variantes antes de alterar o estoque.
     * A ordenação por id evita deadlock quando dois pedidos travam as mesmas variantes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v join fetch v.product where v.storeId = :storeId and v.id in :ids order by v.id")
    List<ProductVariant> lockForUpdate(Long storeId, Collection<Long> ids);

    /** Variantes vendáveis com disponível <= estoque mínimo do produto. */
    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select v from ProductVariant v
            where v.storeId = :storeId and v.active = true and v.product.active = true and v.product.archivedAt is null
              and (v.physicalQty - v.reservedQty) <= v.product.minStock
            order by (v.physicalQty - v.reservedQty), v.sku
            """)
    Page<ProductVariant> findLowStock(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select v from ProductVariant v
            where v.storeId = :storeId and v.product.archivedAt is null
              and (:q = '' or lower(v.sku) like concat('%', :q, '%') or lower(v.product.name) like concat('%', :q, '%'))
              and (:lowOnly = false or (v.physicalQty - v.reservedQty) <= v.product.minStock)
            order by v.product.name, v.id
            """)
    Page<ProductVariant> search(Long storeId, String q, boolean lowOnly, Pageable pageable);
}
