package com.lojafacil.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lojafacil.finance.EntryType;
import com.lojafacil.finance.FinanceService;
import com.lojafacil.finance.FinancialCategory.SystemKey;
import com.lojafacil.finance.FinancialEntry.SourceType;
import com.lojafacil.orders.Order;
import com.lojafacil.orders.OrderRepository;
import com.lojafacil.orders.OrderService;
import com.lojafacil.orders.OrderStatus;
import com.lojafacil.orders.PaymentStatus;
import com.lojafacil.payments.PaymentIntegrationService.Resolved;
import com.lojafacil.payments.PaymentProvider.CheckoutRequest;
import com.lojafacil.payments.PaymentProvider.CheckoutSession;
import com.lojafacil.payments.PaymentProvider.ProviderException;
import com.lojafacil.payments.PaymentProvider.ProviderPayment;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.money.Money;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreRepository;

/**
 * Orquestra cobranças, confirmações, reembolsos e cancelamentos.
 *
 * <h3>Regra de ouro: rede fora da transação</h3>
 * Cada fluxo é dividido em etapas: (1) transação curta para validar e registrar a intenção,
 * (2) chamada ao provedor SEM transação aberta, (3) transação curta para gravar o resultado.
 * Usamos {@link TransactionTemplate} para deixar explícito onde cada transação começa e termina.
 *
 * <h3>Idempotência</h3>
 * O estado final é sempre obtido consultando o provedor (nunca pelo redirecionamento do navegador).
 * Aplicar a mesma situação duas vezes não muda nada: a baixa de estoque é controlada por
 * {@code order.stockCommitted} e os lançamentos financeiros por chave única de origem.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final Payment.Repo payments;
    private final Refund.Repo refunds;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final StoreRepository stores;
    private final FinanceService finance;
    private final PaymentIntegrationService integrations;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public PaymentService(Payment.Repo payments, Refund.Repo refunds, OrderRepository orders, OrderService orderService,
                          StoreRepository stores, FinanceService finance, PaymentIntegrationService integrations,
                          AuditService audit, CurrentUser currentUser, AppProperties props,
                          PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.refunds = refunds;
        this.orders = orders;
        this.orderService = orderService;
        this.stores = stores;
        this.finance = finance;
        this.integrations = integrations;
        this.audit = audit;
        this.currentUser = currentUser;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ criação da cobrança

    /** Registra a cobrança pendente na mesma transação da criação do pedido. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment createPending(Order order, ProviderType provider) {
        Payment p = new Payment();
        p.setStoreId(order.getStoreId());
        p.setOrderId(order.getId());
        p.setProvider(provider);
        p.setSimulated(provider == ProviderType.SIMULATOR);
        p.setAmount(order.getTotal());
        return payments.save(p);
    }

    /**
     * Cria (ou reaproveita) o checkout no provedor. Pode ser chamado de novo com segurança:
     * a mesma chave de idempotência é enviada, então o provedor não duplica a cobrança.
     */
    public CheckoutSession startCheckout(Long paymentId, String trackingUrl) {
        record Data(Payment payment, Order order, Store store) {
        }
        Data d = tx.execute(s -> {
            Payment p = payments.findById(paymentId).orElseThrow(() -> Errors.notFound("Pagamento"));
            Order o = orders.findById(p.getOrderId()).orElseThrow();
            return new Data(p, o, stores.findById(p.getStoreId()).orElseThrow());
        });
        if (d.payment().getCheckoutUrl() != null) {
            return new CheckoutSession(d.payment().getProviderCheckoutId(), d.payment().getCheckoutUrl());
        }
        if (d.payment().getProvider() == ProviderType.MANUAL) {
            throw Errors.rule("MANUAL_PAYMENT", "Este pedido será pago diretamente na loja.");
        }
        Resolved r = integrations.resolve(d.store().getId(), d.payment().getProvider());
        CheckoutSession session = r.provider().createCheckout(r.context(), new CheckoutRequest(
                d.payment().getPublicId(), d.payment().getIdempotencyKey(),
                "Pedido #" + d.order().getNumber() + " - " + d.store().getName(),
                d.payment().getAmount(), d.order().getCustomerName(), d.order().getCustomerEmail(), trackingUrl,
                props.publicApiBaseUrl() + "/api/webhooks/mercadopago/" + d.store().getSlug(),
                d.order().getReservationExpiresAt()));
        tx.executeWithoutResult(s -> payments.findById(paymentId).ifPresent(p -> {
            p.setProviderCheckoutId(session.checkoutId());
            p.setCheckoutUrl(session.checkoutUrl());
        }));
        return session;
    }

    // ------------------------------------------------------------------ confirmação (webhook, retorno, conciliação)

    /** Consulta o provedor pela referência externa e aplica a situação encontrada. */
    public String sync(Long paymentId) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> Errors.notFound("Pagamento"));
        if (p.getProvider() == ProviderType.MANUAL) {
            return "MANUAL";
        }
        Resolved r = integrations.resolve(p.getStoreId(), p.getProvider());
        Optional<ProviderPayment> found = r.provider().findByExternalReference(r.context(), p.getPublicId());
        return found.map(pp -> apply(paymentId, pp)).orElse("NOT_FOUND");
    }

    /** Conferência manual pelo painel ("Consultar pagamento"). */
    public void syncOrder(Long storeId, Long orderId) {
        Payment p = payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .filter(x -> x.getStoreId().equals(storeId))
                .orElseThrow(() -> Errors.notFound("Pagamento"));
        try {
            sync(p.getId());
        } catch (ProviderException e) {
            throw Errors.rule("PAYMENT_PROVIDER_ERROR", e.getMessage());
        }
    }

    /** Notificação do provedor: busca o pagamento pelo id informado e o associa à nossa cobrança. */
    public String processNotification(Long storeId, ProviderType type, String providerPaymentId) {
        Resolved r = integrations.resolve(storeId, type);
        Optional<ProviderPayment> found = r.provider().findPayment(r.context(), providerPaymentId);
        if (found.isEmpty()) {
            return "PAYMENT_NOT_FOUND";
        }
        ProviderPayment pp = found.get();
        if (pp.externalReference() == null) {
            return "NO_EXTERNAL_REFERENCE";
        }
        UUID ref;
        try {
            ref = UUID.fromString(pp.externalReference());
        } catch (IllegalArgumentException e) {
            return "FOREIGN_REFERENCE";
        }
        Optional<Payment> ours = payments.findByPublicId(ref).filter(p -> p.getStoreId().equals(storeId));
        return ours.map(p -> apply(p.getId(), pp)).orElse("UNKNOWN_REFERENCE");
    }

    /**
     * Aplica a situação informada pelo provedor ao pagamento e ao pedido. Trava pedido e pagamento,
     * confere loja, referência, valor e moeda, e trata notificações repetidas ou fora de ordem.
     */
    public String apply(Long paymentId, ProviderPayment pp) {
        return tx.execute(s -> {
            Payment snapshot = payments.findById(paymentId).orElseThrow(() -> Errors.notFound("Pagamento"));
            Order order = orders.lockById(snapshot.getOrderId()).orElseThrow();
            Payment p = payments.lockById(paymentId).orElseThrow();

            if (p.getProvider() != ProviderType.MANUAL && !p.getPublicId().toString().equals(pp.externalReference())) {
                return attention(order, "Notificação com referência divergente foi ignorada.", "REFERENCE_MISMATCH");
            }
            if (!"BRL".equalsIgnoreCase(pp.currency()) || pp.amount().compareTo(p.getAmount()) != 0) {
                return attention(order, "Pagamento informado com valor ou moeda diferente do pedido ("
                        + pp.currency() + " " + pp.amount() + "). Confira no provedor.", "AMOUNT_MISMATCH");
            }
            if (pp.liveMode() && !props.payments().allowLiveTransactions()) {
                return attention(order, "Transação de produção recebida em ambiente de teste e bloqueada.", "LIVE_BLOCKED");
            }
            if (pp.providerPaymentId() != null && p.getProvider() != ProviderType.MANUAL) {
                p.setProviderPaymentId(pp.providerPaymentId());
            }
            p.setProviderUpdatedAt(Instant.now());

            PaymentStatus target = pp.status();
            boolean targetPaid = target == PaymentStatus.APPROVED || target == PaymentStatus.PARTIALLY_REFUNDED
                    || target == PaymentStatus.REFUNDED;

            if (!targetPaid) {
                // Notificação antiga chegando depois da aprovação: ignorada (nunca "desaprova")
                if (p.getStatus().isPaid() || p.getStatus() == PaymentStatus.REFUNDED) {
                    return "IGNORED_STALE";
                }
                if (target == p.getStatus()) {
                    return "UNCHANGED";
                }
                if (target == PaymentStatus.DECLINED && p.getStatus() == PaymentStatus.PENDING) {
                    p.setStatus(PaymentStatus.DECLINED);
                    orderService.onPaymentStatus(order, PaymentStatus.DECLINED, "Pagamento recusado pelo provedor (" + pp.rawStatus() + ")");
                } else if (target == PaymentStatus.PENDING && p.getStatus() == PaymentStatus.DECLINED) {
                    p.setStatus(PaymentStatus.PENDING);
                    orderService.onPaymentStatus(order, PaymentStatus.PENDING, "Nova tentativa de pagamento em análise");
                } else if (target == PaymentStatus.EXPIRED && order.getStatus() == OrderStatus.OPEN) {
                    p.setStatus(PaymentStatus.EXPIRED);
                    orderService.expire(order);
                }
                return target.name();
            }

            // 1) primeira aprovação: confirma pedido, baixa estoque e lança a receita
            if (!p.getStatus().isPaid() && p.getStatus() != PaymentStatus.REFUNDED) {
                p.setStatus(PaymentStatus.APPROVED);
                p.setFeeAmount(Money.normalize(pp.feeAmount()));
                p.setMethod(pp.method());
                orderService.onPaymentApproved(order, (p.isSimulated() ? "[SIMULADO] " : "") + "Pagamento aprovado"
                        + (pp.method() != null ? " (" + pp.method() + ")" : ""));
                LocalDate today = FinanceService.today();
                finance.recordIntegration(p.getStoreId(), EntryType.INCOME, SystemKey.SALES, p.getAmount(), today,
                        "Venda - pedido #" + order.getNumber() + (p.isSimulated() ? " [simulado]" : ""),
                        SourceType.ORDER_PAYMENT, "payment:" + p.getId(), order.getId(), p.getId(), order.getCustomerId());
                finance.recordIntegration(p.getStoreId(), EntryType.EXPENSE, SystemKey.FEES, p.getFeeAmount(), today,
                        "Taxa de pagamento - pedido #" + order.getNumber() + (p.isSimulated() ? " [simulada]" : ""),
                        SourceType.PAYMENT_FEE, "payment:" + p.getId() + ":fee", order.getId(), p.getId(), null);
            }

            // 2) reembolsos informados pelo provedor (feitos por nós ou direto no painel do provedor)
            if (pp.refundedAmount().compareTo(p.getRefundedAmount()) > 0) {
                BigDecimal diff = pp.refundedAmount().subtract(p.getRefundedAmount());
                for (Refund r : refunds.findByPaymentIdAndStatusOrderById(p.getId(), Refund.Status.REQUESTED)) {
                    if (r.getAmount().compareTo(diff) <= 0) {
                        completeRefund(order, p, r);
                        diff = diff.subtract(r.getAmount());
                    }
                }
                if (diff.signum() > 0) {
                    Refund external = new Refund();
                    external.setPaymentId(p.getId());
                    external.setAmount(diff);
                    external.setReason("Reembolso registrado diretamente no provedor");
                    refunds.save(external);
                    completeRefund(order, p, external);
                }
            }
            return p.getStatus().name();
        });
    }

    // ------------------------------------------------------------------ reembolso

    /** Reembolso total ou parcial (Proprietário/Gerente). */
    public void refund(Long storeId, Long orderId, BigDecimal rawAmount, String reason) {
        BigDecimal amount = Money.normalize(rawAmount);
        if (amount.signum() <= 0) {
            throw Errors.badRequest("INVALID_AMOUNT", "Informe um valor positivo.");
        }
        // Etapa 1: registra a intenção (REQUESTED) com chave de idempotência própria
        record Plan(Long refundId, Long paymentId, ProviderType provider, String providerPaymentId, UUID key) {
        }
        Plan plan = tx.execute(s -> {
            Order order = orderService.lockForStore(storeId, orderId);
            Payment p = payments.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                    .filter(x -> x.getStatus().isPaid())
                    .orElseThrow(() -> Errors.rule("NOT_PAID", "O pedido não possui pagamento aprovado para reembolsar."));
            BigDecimal pending = refunds.findByPaymentIdAndStatusOrderById(p.getId(), Refund.Status.REQUESTED).stream()
                    .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal available = p.refundable().subtract(pending);
            if (amount.compareTo(available) > 0) {
                throw Errors.rule("REFUND_EXCEEDS", "Valor maior que o disponível para reembolso (R$ " + available + ").");
            }
            Refund r = new Refund();
            r.setPaymentId(p.getId());
            r.setAmount(amount);
            r.setReason(reason);
            r.setCreatedBy(currentUser.idOrNull());
            refunds.save(r);
            return new Plan(r.getId(), p.getId(), p.getProvider(), p.getProviderPaymentId(), r.getIdempotencyKey());
        });

        // Etapa 2: chamada ao provedor, sem transação aberta
        if (plan.provider() != ProviderType.MANUAL) {
            try {
                Resolved r = integrations.resolve(storeId, plan.provider());
                r.provider().refund(r.context(), plan.providerPaymentId(), amount, plan.key());
            } catch (ProviderException e) {
                tx.executeWithoutResult(s -> refunds.findById(plan.refundId()).ifPresent(r -> r.setStatus(Refund.Status.FAILED)));
                throw Errors.rule("REFUND_FAILED", e.getMessage());
            }
        }

        // Etapa 3: grava o resultado (se um webhook já tiver concluído este reembolso, nada muda)
        tx.executeWithoutResult(s -> {
            Order order = orders.lockById(orderId).orElseThrow();
            Payment p = payments.lockById(plan.paymentId()).orElseThrow();
            Refund r = refunds.findById(plan.refundId()).orElseThrow();
            if (r.getStatus() == Refund.Status.REQUESTED) {
                completeRefund(order, p, r);
            }
            audit.record(storeId, "ORDER", orderId, "REFUND", "#" + order.getNumber() + " R$ " + amount + " - " + reason);
        });
    }

    private void completeRefund(Order order, Payment p, Refund r) {
        r.setStatus(Refund.Status.COMPLETED);
        p.setRefundedAmount(p.getRefundedAmount().add(r.getAmount()));
        PaymentStatus status = p.getRefundedAmount().compareTo(p.getAmount()) >= 0
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        p.setStatus(status);
        orderService.onPaymentStatus(order, status, "Reembolso de R$ " + r.getAmount()
                + (r.getReason() != null ? " - " + r.getReason() : ""));
        finance.recordIntegration(p.getStoreId(), EntryType.EXPENSE, SystemKey.REFUNDS, r.getAmount(), FinanceService.today(),
                "Reembolso - pedido #" + order.getNumber() + (p.isSimulated() ? " [simulado]" : ""),
                SourceType.REFUND, "refund:" + r.getId(), order.getId(), p.getId(), order.getCustomerId());
    }

    // ------------------------------------------------------------------ cancelamento

    /**
     * Cancela o pedido: sem pagamento, libera a reserva; pago e ainda não enviado, reembolsa o valor
     * restante e devolve os itens ao estoque. Pedidos enviados/entregues usam o fluxo de devolução.
     */
    public void cancelOrder(Long storeId, Long orderId, String reason) {
        record State(PaymentStatus paymentStatus, BigDecimal refundable) {
        }
        State st = tx.execute(s -> {
            Order order = orderService.lockForStore(storeId, orderId);
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
                throw Errors.rule("INVALID_TRANSITION", "Este pedido não pode mais ser cancelado.");
            }
            if (!order.getPaymentStatus().isPaid() && order.getPaymentStatus() != PaymentStatus.REFUNDED) {
                orderService.cancelUnpaid(order, reason);
                payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId).ifPresent(p -> {
                    if (p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.DECLINED) {
                        p.setStatus(PaymentStatus.EXPIRED);
                    }
                });
                return new State(null, BigDecimal.ZERO);
            }
            OrderService.requireCancellableAfterPayment(order);
            BigDecimal refundable = payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                    .map(Payment::refundable).orElse(BigDecimal.ZERO);
            return new State(order.getPaymentStatus(), refundable);
        });
        if (st.paymentStatus() == null) {
            return;
        }
        if (st.refundable().signum() > 0) {
            refund(storeId, orderId, st.refundable(), "Cancelamento: " + reason);
        }
        tx.executeWithoutResult(s -> orderService.cancelAfterFullRefund(orderService.lockForStore(storeId, orderId), reason));
    }

    // ------------------------------------------------------------------ recebimento manual

    /** Registra pagamento feito fora do sistema (dinheiro, maquininha, Pix direto). */
    public void registerManualPayment(Long storeId, Long orderId, String method) {
        Long paymentId = tx.execute(s -> {
            Order order = orderService.lockForStore(storeId, orderId);
            Payment p = payments.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                    .orElseThrow(() -> Errors.notFound("Pagamento"));
            if (p.getProvider() != ProviderType.MANUAL) {
                throw Errors.rule("ONLINE_PAYMENT", "Este pedido usa pagamento online; a confirmação vem do provedor.");
            }
            if (p.getStatus().isPaid()) {
                throw Errors.rule("ALREADY_PAID", "O pagamento deste pedido já foi registrado.");
            }
            return p.getId();
        });
        Payment p = payments.findById(paymentId).orElseThrow();
        apply(paymentId, new ProviderPayment("manual-" + paymentId, p.getPublicId().toString(), PaymentStatus.APPROVED,
                "manual", p.getAmount(), "BRL", BigDecimal.ZERO, BigDecimal.ZERO, method, false));
    }

    // ------------------------------------------------------------------ expiração

    /**
     * Expira um pedido cuja reserva venceu. Antes, consulta o provedor: se o pagamento foi aprovado
     * e o webhook se perdeu, o pedido é confirmado em vez de cancelado.
     */
    public void expireOrder(Long orderId) {
        payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .filter(p -> p.getProvider() != ProviderType.MANUAL && p.getStatus() == PaymentStatus.PENDING)
                .ifPresent(p -> {
                    try {
                        sync(p.getId());
                    } catch (RuntimeException e) {
                        log.warn("Não foi possível conferir o pagamento {} antes de expirar: {}", p.getId(), e.getMessage());
                    }
                });
        tx.executeWithoutResult(s -> {
            Order order = orders.lockById(orderId).orElseThrow();
            if (order.getReservationExpiresAt() != null && order.getReservationExpiresAt().isAfter(Instant.now())) {
                return;
            }
            if (orderService.expire(order)) {
                payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId).ifPresent(p -> {
                    if (p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.DECLINED) {
                        p.setStatus(PaymentStatus.EXPIRED);
                    }
                });
            }
        });
    }

    private String attention(Order order, String reason, String code) {
        order.flagAttention(reason);
        log.warn("Pagamento do pedido {} exige atenção: {}", order.getId(), code);
        return code;
    }
}
