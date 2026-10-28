package com.lojafacil.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
@Table(name = "refunds")
@Getter
@Setter
public class Refund {
    public enum Status { REQUESTED, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    private BigDecimal amount;

    @Column(nullable = false, unique = true)
    private UUID idempotencyKey = UUID.randomUUID();

    private String providerRefundId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.REQUESTED;

    private String reason;
    private Long createdBy;
    private Instant createdAt = Instant.now();

    public interface Repo extends JpaRepository<Refund, Long> {
        List<Refund> findByPaymentIdAndStatusOrderById(Long paymentId, Status status);

        List<Refund> findByPaymentIdOrderById(Long paymentId);
    }
}
