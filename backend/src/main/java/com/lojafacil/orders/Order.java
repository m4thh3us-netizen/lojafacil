package com.lojafacil.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "SalesOrder") // "Order" é palavra reservada em JPQL
@Table(name = "orders")
@Getter
@Setter
public class Order {

    public enum Channel { ONLINE, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    /** Número sequencial por loja, exibido ao cliente. Não dá acesso ao pedido sozinho. */
    private int number;

    /** Hash do token do link de acompanhamento (o token em si só existe no link enviado ao cliente). */
    @Column(nullable = false)
    private String trackingTokenHash;

    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    @Enumerated(EnumType.STRING)
    private DeliveryMethod deliveryMethod;

    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discountTotal = BigDecimal.ZERO;
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.AWAITING;

    /** Garante que a baixa de estoque da venda acontece uma única vez. */
    private boolean stockCommitted;

    /** Situações que exigem ação humana (ex.: pagamento aprovado sem estoque). */
    private boolean requiresAttention;
    private String attentionReason;

    private String notes;
    private Instant reservationExpiresAt;
    private Instant createdAt = Instant.now();
    private Instant confirmedAt;
    private Instant cancelledAt;
    private Instant completedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    public void flagAttention(String reason) {
        this.requiresAttention = true;
        this.attentionReason = reason;
    }
}
