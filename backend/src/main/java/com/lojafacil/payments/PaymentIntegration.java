package com.lojafacil.payments;

import java.time.Instant;
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
@Table(name = "payment_integrations")
@Getter
@Setter
public class PaymentIntegration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    private ProviderType provider = ProviderType.SIMULATOR;

    private String accessTokenEnc;
    private String publicKey;
    private String webhookSecretEnc;
    private Instant lastTestAt;
    private Boolean lastTestOk;
    private Long updatedBy;
    private Instant updatedAt = Instant.now();

    public interface Repo extends JpaRepository<PaymentIntegration, Long> {
        Optional<PaymentIntegration> findByStoreId(Long storeId);
    }
}
