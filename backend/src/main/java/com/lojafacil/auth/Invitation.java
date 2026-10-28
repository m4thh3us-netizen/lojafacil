package com.lojafacil.auth;

import java.time.Instant;
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

/** Convite enviado pelo proprietário para um colaborador entrar na loja. */
@Entity
@Table(name = "user_invitations")
@Getter
@Setter
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Long invitedBy;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant acceptedAt;
    private Instant revokedAt;
    private Instant createdAt = Instant.now();

    public boolean isPending(Instant now) {
        return acceptedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public interface Repo extends JpaRepository<Invitation, Long> {
        Optional<Invitation> findByTokenHash(String tokenHash);

        List<Invitation> findByStoreIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(Long storeId);

        Optional<Invitation> findByIdAndStoreId(Long id, Long storeId);
    }
}
