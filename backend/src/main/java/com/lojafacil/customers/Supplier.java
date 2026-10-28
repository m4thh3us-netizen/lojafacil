package com.lojafacil.customers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String name;

    private String contactName;
    private String phone;
    private String email;
    private String address;
    private String notes;
    private boolean active = true;
    private Instant createdAt = Instant.now();

    public interface Repo extends JpaRepository<Supplier, Long> {
        Optional<Supplier> findByIdAndStoreId(Long id, Long storeId);

        List<Supplier> findByStoreIdAndActiveTrueOrderByName(Long storeId);

        @Query("""
                select s from Supplier s where s.storeId = :storeId
                  and (:q = '' or lower(s.name) like concat('%', :q, '%') or lower(coalesce(s.contactName, '')) like concat('%', :q, '%')
                       or lower(coalesce(s.email, '')) like concat('%', :q, '%'))
                """)
        Page<Supplier> search(Long storeId, String q, Pageable pageable);
    }
}
