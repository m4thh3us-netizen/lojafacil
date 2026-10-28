package com.lojafacil.payments.simulator;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.orders.PaymentStatus;
import com.lojafacil.payments.PaymentProvider;
import com.lojafacil.payments.ProviderType;
import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.money.Money;

@Component
public class SimulatedPaymentProvider implements PaymentProvider {
    static final BigDecimal FEE_PERCENT = new BigDecimal("3.99");

    private final SimulatorCharge.Repo charges;
    private final AppProperties props;

    public SimulatedPaymentProvider(SimulatorCharge.Repo charges, AppProperties props) {
        this.charges = charges;
        this.props = props;
    }

    @Override
    public ProviderType type() {
        return ProviderType.SIMULATOR;
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckoutSession createCheckout(ProviderContext ctx, CheckoutRequest req) {
        SimulatorCharge charge = charges.findByIdempotencyKey(req.idempotencyKey()).orElseGet(() -> {
            SimulatorCharge c = new SimulatorCharge();
            c.setStoreId(ctx.storeId());
            c.setExternalReference(req.externalReference());
            c.setIdempotencyKey(req.idempotencyKey());
            c.setDescription(req.description());
            c.setAmount(req.amount());
            c.setReturnUrl(req.returnUrl());
            return charges.save(c);
        });
        return new CheckoutSession(charge.getId().toString(),
                props.frontendBaseUrl() + "/pagamento-simulado/" + charge.getId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ProviderPayment> findPayment(ProviderContext ctx, String providerPaymentId) {
        return charges.findById(UUID.fromString(providerPaymentId))
                .filter(c -> c.getStoreId().equals(ctx.storeId()))
                .map(SimulatedPaymentProvider::toProviderPayment);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ProviderPayment> findByExternalReference(ProviderContext ctx, UUID externalReference) {
        return charges.findByExternalReferenceOrderByCreatedAtDesc(externalReference).stream()
                .filter(c -> c.getStoreId().equals(ctx.storeId()))
                .findFirst()
                .map(SimulatedPaymentProvider::toProviderPayment);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProviderRefund refund(ProviderContext ctx, String providerPaymentId, BigDecimal amount, UUID idempotencyKey) {
        SimulatorCharge c = charges.findById(UUID.fromString(providerPaymentId))
                .filter(x -> x.getStoreId().equals(ctx.storeId()))
                .orElseThrow(() -> new ProviderException("Cobrança simulada não encontrada."));
        if (c.getStatus() != SimulatorCharge.Status.APPROVED && c.getStatus() != SimulatorCharge.Status.PARTIALLY_REFUNDED) {
            throw new ProviderException("A cobrança simulada não está aprovada.");
        }
        BigDecimal newTotal = c.getRefundedAmount().add(amount);
        if (newTotal.compareTo(c.getAmount()) > 0) {
            throw new ProviderException("Valor de reembolso maior que o disponível.");
        }
        c.setRefundedAmount(newTotal);
        c.setStatus(newTotal.compareTo(c.getAmount()) == 0 ? SimulatorCharge.Status.REFUNDED : SimulatorCharge.Status.PARTIALLY_REFUNDED);
        return new ProviderRefund("sim-refund-" + idempotencyKey, amount);
    }

    @Override
    public ConnectionTest testConnection(ProviderContext ctx) {
        return new ConnectionTest(true, "Simulador local ativo. Os pagamentos são fictícios e identificados como SIMULADOS.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SimulatorCharge decide(UUID chargeId, boolean approve) {
        SimulatorCharge c = charges.findById(chargeId).orElseThrow(() -> Errors.notFound("Cobrança simulada"));
        if (c.getStatus() == SimulatorCharge.Status.APPROVED || c.getStatus() == SimulatorCharge.Status.REFUNDED
                || c.getStatus() == SimulatorCharge.Status.PARTIALLY_REFUNDED) {
            return c;
        }
        c.setStatus(approve ? SimulatorCharge.Status.APPROVED : SimulatorCharge.Status.REJECTED);
        return c;
    }

    @Transactional(readOnly = true)
    public SimulatorCharge get(UUID chargeId) {
        return charges.findById(chargeId).orElseThrow(() -> Errors.notFound("Cobrança simulada"));
    }

    static ProviderPayment toProviderPayment(SimulatorCharge c) {
        PaymentStatus status = switch (c.getStatus()) {
            case PENDING -> PaymentStatus.PENDING;
            case APPROVED -> PaymentStatus.APPROVED;
            case REJECTED -> PaymentStatus.DECLINED;
            case PARTIALLY_REFUNDED -> PaymentStatus.PARTIALLY_REFUNDED;
            case REFUNDED -> PaymentStatus.REFUNDED;
        };
        BigDecimal fee = c.getStatus() == SimulatorCharge.Status.PENDING || c.getStatus() == SimulatorCharge.Status.REJECTED
                ? Money.ZERO : Money.percentOf(c.getAmount(), FEE_PERCENT);
        return new ProviderPayment(c.getId().toString(), c.getExternalReference().toString(), status, c.getStatus().name(),
                c.getAmount(), "BRL", c.getRefundedAmount(), fee, "Simulado", false);
    }
}
