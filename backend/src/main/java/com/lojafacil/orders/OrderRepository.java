package com.lojafacil.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByIdAndStoreId(Long id, Long storeId);

    Optional<Order> findByTrackingTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<Order> lockById(Long id);

    Page<Order> findByStoreIdAndCustomerIdOrderByCreatedAtDesc(Long storeId, Long customerId, Pageable pageable);

    long countByStoreIdAndCustomerId(Long storeId, Long customerId);

    @Query("""
            select coalesce(sum(o.total), 0) from SalesOrder o
            where o.storeId = :storeId and o.customerId = :customerId and o.status in ('CONFIRMED', 'COMPLETED')
            """)
    BigDecimal totalConfirmedForCustomer(Long storeId, Long customerId);

    @Query("select count(o) from SalesOrder o where o.storeId = :storeId and o.status in ('OPEN', 'CONFIRMED') and o.fulfillmentStatus in ('AWAITING','PICKING','READY_FOR_PICKUP','SHIPPED')")
    long countPending(Long storeId);

    @Query("select count(o) from SalesOrder o where o.storeId = :storeId and o.requiresAttention = true")
    long countAttention(Long storeId);

    @Query("""
            select coalesce(sum(o.total), 0) from SalesOrder o
            where o.storeId = :storeId and o.status in ('CONFIRMED','COMPLETED') and o.confirmedAt >= :from and o.confirmedAt < :to
            """)
    BigDecimal salesTotal(Long storeId, Instant from, Instant to);

    @Query("""
            select count(o) from SalesOrder o
            where o.storeId = :storeId and o.status in ('CONFIRMED','COMPLETED') and o.confirmedAt >= :from and o.confirmedAt < :to
            """)
    long salesCount(Long storeId, Instant from, Instant to);

    @Query("""
            select coalesce(sum(i.unitCost * i.quantity), 0) from OrderItem i
            where i.order.storeId = :storeId and i.order.status in ('CONFIRMED','COMPLETED')
              and i.order.confirmedAt >= :from and i.order.confirmedAt < :to
            """)
    BigDecimal costOfGoodsSold(Long storeId, Instant from, Instant to);
}
