package com.lojafacil.finance;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "financial_categories")
@Getter
@Setter
public class FinancialCategory {
    public enum SystemKey { SALES, FEES, REFUNDS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    @Enumerated(EnumType.STRING)
    private SystemKey systemKey;

    private boolean active = true;

    public interface Repo extends JpaRepository<FinancialCategory, Long> {
        List<FinancialCategory> findByStoreIdOrderByTypeAscNameAsc(Long storeId);

        Optional<FinancialCategory> findByIdAndStoreId(Long id, Long storeId);

        Optional<FinancialCategory> findByStoreIdAndSystemKey(Long storeId, SystemKey key);

        boolean existsByStoreIdAndTypeAndNameIgnoreCase(Long storeId, EntryType type, String name);
    }
}
