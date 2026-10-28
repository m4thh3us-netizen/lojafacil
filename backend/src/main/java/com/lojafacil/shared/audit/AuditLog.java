package com.lojafacil.shared.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Registro imutável de ações sensíveis (estoque, pedidos, financeiro, configurações). */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    private Long userId;

    @Column(nullable = false)
    private String entityType;

    private Long entityId;

    @Column(nullable = false)
    private String action;

    private String details;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLog(Long storeId, Long userId, String entityType, Long entityId, String action, String details) {
        this.storeId = storeId;
        this.userId = userId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.details = details != null && details.length() > 2000 ? details.substring(0, 2000) : details;
    }
}
