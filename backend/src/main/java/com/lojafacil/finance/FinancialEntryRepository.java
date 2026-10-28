package com.lojafacil.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface FinancialEntryRepository extends JpaRepository<FinancialEntry, Long>, JpaSpecificationExecutor<FinancialEntry> {
    @EntityGraph(attributePaths = {"category"})
    Optional<FinancialEntry> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndSourceTypeAndSourceRef(Long storeId, FinancialEntry.SourceType type, String sourceRef);

    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<FinancialEntry> findAll(org.springframework.data.jpa.domain.Specification<FinancialEntry> spec, Pageable pageable);

    @Query("""
            select e.paidDate, e.type, sum(e.amount) from FinancialEntry e
            where e.storeId = :storeId and e.status = com.lojafacil.finance.FinancialEntry.Status.PAID
              and e.paidDate >= :from and e.paidDate <= :to
            group by e.paidDate, e.type
            """)
    List<Object[]> realizedByDay(Long storeId, LocalDate from, LocalDate to);

    @Query("""
            select e.dueDate, e.type, sum(e.amount) from FinancialEntry e
            where e.storeId = :storeId and e.status = com.lojafacil.finance.FinancialEntry.Status.PENDING
              and e.dueDate >= :from and e.dueDate <= :to
            group by e.dueDate, e.type
            """)
    List<Object[]> forecastByDay(Long storeId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(case when e.type = com.lojafacil.finance.EntryType.INCOME then e.amount else -e.amount end), 0)
            from FinancialEntry e
            where e.storeId = :storeId and e.status = com.lojafacil.finance.FinancialEntry.Status.PAID and e.paidDate < :before
            """)
    BigDecimal realizedBalanceBefore(Long storeId, LocalDate before);

    @Query("""
            select e.type, e.sourceType, c.systemKey, sum(e.amount) from FinancialEntry e join e.category c
            where e.storeId = :storeId and e.status = com.lojafacil.finance.FinancialEntry.Status.PAID
              and e.paidDate >= :from and e.paidDate <= :to
            group by e.type, e.sourceType, c.systemKey
            """)
    List<Object[]> paidBreakdown(Long storeId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(e.amount), 0) from FinancialEntry e join e.category c
            where e.storeId = :storeId and e.type = com.lojafacil.finance.EntryType.EXPENSE
              and e.status <> com.lojafacil.finance.FinancialEntry.Status.CANCELLED
              and e.sourceType = com.lojafacil.finance.FinancialEntry.SourceType.MANUAL
              and e.dueDate >= :from and e.dueDate <= :to
            """)
    BigDecimal operatingExpensesByDue(Long storeId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(e.amount), 0) from FinancialEntry e
            where e.storeId = :storeId and e.sourceType = com.lojafacil.finance.FinancialEntry.SourceType.REVERSAL
              and e.type = com.lojafacil.finance.EntryType.INCOME
              and e.dueDate >= :from and e.dueDate <= :to
            """)
    BigDecimal expenseReversalsByDue(Long storeId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(e.amount), 0) from FinancialEntry e
            where e.storeId = :storeId and e.type = :type and e.status = com.lojafacil.finance.FinancialEntry.Status.PENDING
            """)
    BigDecimal openTotal(Long storeId, EntryType type);

    @Query("""
            select coalesce(sum(e.amount), 0) from FinancialEntry e
            where e.storeId = :storeId and e.type = :type and e.status = com.lojafacil.finance.FinancialEntry.Status.PENDING
              and e.dueDate < :today
            """)
    BigDecimal overdueTotal(Long storeId, EntryType type, LocalDate today);
}
