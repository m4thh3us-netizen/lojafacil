package com.lojafacil.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.lojafacil.orders.PaymentStatus;

/**
 * Contrato comum dos provedores de pagamento. O restante do sistema só conhece esta interface,
 * então trocar o simulador pelo Mercado Pago (ou, no futuro, outro gateway) não muda as regras de negócio.
 *
 * Importante: nenhuma implementação é chamada com transação de banco aberta — chamadas de rede podem
 * demorar e não devem segurar travas de pedido ou estoque.
 */
public interface PaymentProvider {

    ProviderType type();

    /** true quando os pagamentos não movimentam dinheiro real (simulador). */
    boolean simulated();

    /** Cria a cobrança e devolve a URL do checkout hospedado. Deve ser idempotente pela chave informada. */
    CheckoutSession createCheckout(ProviderContext ctx, CheckoutRequest request);

    /** Consulta um pagamento pelo id no provedor. */
    Optional<ProviderPayment> findPayment(ProviderContext ctx, String providerPaymentId);

    /** Consulta o pagamento mais relevante associado à nossa referência externa. */
    Optional<ProviderPayment> findByExternalReference(ProviderContext ctx, UUID externalReference);

    /** Reembolso total ou parcial. Deve ser idempotente pela chave informada. */
    ProviderRefund refund(ProviderContext ctx, String providerPaymentId, BigDecimal amount, UUID idempotencyKey);

    /** Teste de credenciais; devolve uma descrição amigável do resultado. */
    ConnectionTest testConnection(ProviderContext ctx);

    // ------------------------------------------------------------------ tipos auxiliares

    record ProviderContext(Long storeId, String storeSlug, String accessToken, String webhookSecret) {
    }

    record CheckoutRequest(UUID externalReference, UUID idempotencyKey, String description, BigDecimal amount,
                           String payerName, String payerEmail, String returnUrl, String notificationUrl,
                           Instant expiresAt) {
    }

    record CheckoutSession(String checkoutId, String checkoutUrl) {
    }

    /** Situação do pagamento normalizada para os nossos estados. */
    record ProviderPayment(String providerPaymentId, String externalReference, PaymentStatus status, String rawStatus,
                           BigDecimal amount, String currency, BigDecimal refundedAmount, BigDecimal feeAmount,
                           String method, boolean liveMode) {
    }

    record ProviderRefund(String refundId, BigDecimal amount) {
    }

    record ConnectionTest(boolean ok, String message) {
    }

    /** Falha de comunicação ou recusa do provedor (mensagem segura, sem credenciais). */
    class ProviderException extends RuntimeException {
        public ProviderException(String message) {
            super(message);
        }
    }
}
