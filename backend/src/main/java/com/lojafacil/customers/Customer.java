package com.lojafacil.customers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Cliente da loja. As observações internas nunca são enviadas à vitrine pública. */
@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    /** Observações internas da equipe. */
    private String notes;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CustomerAddress> addresses = new ArrayList<>();

    public interface Repo extends JpaRepository<Customer, Long> {

        Optional<Customer> findByIdAndStoreId(Long id, Long storeId);

        @Query("select c from Customer c where c.storeId = :storeId and lower(c.email) = lower(:email)")
        Optional<Customer> findByEmail(Long storeId, String email);

        /**
         * Cria o cliente se o e-mail ainda não existir na loja. Seguro para checkouts simultâneos:
         * o banco resolve a disputa pelo índice único e nenhum dos dois pedidos falha.
         * Retorna 1 se inseriu, 0 se já existia.
         */
        @org.springframework.data.jpa.repository.Modifying
        @Query(value = """
                insert into customers (store_id, name, email, phone)
                values (:storeId, :name, :email, :phone)
                on conflict (store_id, lower(email)) do nothing
                """, nativeQuery = true)
        int insertIfAbsent(Long storeId, String name, String email, String phone);

        @Query("""
                select c from Customer c where c.storeId = :storeId
                  and (:q = '' or lower(c.name) like concat('%', :q, '%') or lower(c.email) like concat('%', :q, '%')
                       or coalesce(c.phone, '') like concat('%', :q, '%'))
                """)
        Page<Customer> search(Long storeId, String q, Pageable pageable);
    }
}
