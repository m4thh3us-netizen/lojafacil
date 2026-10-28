package com.lojafacil.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Lançamento financeiro (conta a pagar/receber ou movimento já realizado).
 *
 * <ul>
 *   <li>PENDING: previsto (conta a pagar ou a receber).</li>
 *   <li>PAID: realizado (pago ou recebido) — não é mais editado nem apagado.</li>
 *   <li>CANCELLED: previsão cancelada antes de ser realizada.</li>
 * </ul>
 * Correções de lançamentos realizados são feitas por ESTORNO: um novo lançamento do tipo oposto,
 * vinculado ao original. O histórico nunca é apagado.
 */
@Entity
@Table(name = "financial_entries")
@Getter
@Setter
public class FinancialEntry {

    public enum Status { PENDING, PAID, CANCELLED }

    public enum SourceType { MANUAL, ORDER_PAYMENT, PAYMENT_FEE, REFUND, REVERSAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private FinancialCategory category;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    private String description;
    private BigDecimal amount;
    private LocalDate dueDate;
    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private UUID installmentGroup;
    private Integer installmentNumber;
    private Integer installmentCount;
    private String counterparty;
    private Long supplierId;
    private Long customerId;
    private Long orderId;
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType = SourceType.MANUAL;

    /** Identificador do evento de origem (ex.: "payment:42"). Único por loja: impede lançamentos duplicados. */
    private String sourceRef;

    private Long reversalOfId;
    private boolean reversed;
    private String notes;
    private Long createdBy;
    private Instant createdAt = Instant.now();

    @Version
    private long version;
}
