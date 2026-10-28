package com.lojafacil.payments.simulator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "simulator_charges")
@Getter
@Setter
public class SimulatorCharge {
    public enum Status { PENDING, APPROVED, REJECTED, REFUNDED, PARTIALLY_REFUNDED }

    @Id
    private UUID id = UUID.randomUUID();

    private Long storeId;
    private UUID externalReference;
    private UUID idempotencyKey;
    private String description;
    private BigDecimal amount;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status = Status.PENDING;

    private BigDecimal refundedAmount = BigDecimal.ZERO;
    private String returnUrl;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public interface Repo extends JpaRepository<SimulatorCharge, UUID> {
        Optional<SimulatorCharge> findByIdempotencyKey(UUID key);

        List<SimulatorCharge> findByExternalReferenceOrderByCreatedAtDesc(UUID externalReference);
    }
}
