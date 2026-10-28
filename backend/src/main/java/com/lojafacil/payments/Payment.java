package com.lojafacil.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.lojafacil.orders.PaymentStatus;

/**
 * Cobrança de um pedido. Nunca guardamos dados de cartão: o cliente paga na página do provedor.
 * {@code publicId} é a referência externa enviada ao provedor para associar o pagamento ao pedido.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderType provider;

    @Column(nullable = false, unique = true)
    private UUID publicId = UUID.randomUUID();

    /** Enviada ao provedor para que tentativas repetidas não criem cobranças duplicadas. */
    @Column(nullable = false, unique = true)
    private UUID idempotencyKey = UUID.randomUUID();

    private String providerPaymentId;
    private String providerCheckoutId;
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    private BigDecimal amount;
    private String currency = "BRL";
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    private BigDecimal feeAmount = BigDecimal.ZERO;
    private String method;
    private boolean simulated;
    private Instant providerUpdatedAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public BigDecimal refundable() {
        return amount.subtract(refundedAmount);
    }

    public interface Repo extends JpaRepository<Payment, Long> {

        List<Payment> findByOrderIdOrderByCreatedAt(Long orderId);

        Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

        Optional<Payment> findByPublicId(UUID publicId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select p from Payment p where p.id = :id")
        Optional<Payment> lockById(Long id);

        /** Pagamentos online pendentes há algum tempo: conferidos no provedor caso um webhook tenha se perdido. */
        @Query("""
                select p from Payment p where p.status = com.lojafacil.orders.PaymentStatus.PENDING
                  and p.provider <> com.lojafacil.payments.ProviderType.MANUAL and p.createdAt < :before
                order by p.createdAt
                """)
        List<Payment> findPendingOnlineBefore(Instant before, Pageable pageable);
    }
}
