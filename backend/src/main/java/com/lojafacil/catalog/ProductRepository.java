package com.lojafacil.catalog;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findByIdAndStoreId(Long id, Long storeId);

    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findByStoreIdAndSlug(Long storeId, String slug);

    boolean existsByStoreIdAndSlug(Long storeId, String slug);

    boolean existsByStoreIdAndSku(Long storeId, String sku);

    @Query("select count(i) > 0 from OrderItem i where i.productId = :productId")
    boolean isUsedInOrders(Long productId);

    @Query("select count(m) > 0 from StockMovement m where m.variant.product.id = :productId")
    boolean hasStockMovements(Long productId);
}
