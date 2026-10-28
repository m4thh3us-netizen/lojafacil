package com.lojafacil.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.lojafacil.orders.PaymentStatus;

public interface PaymentProvider {
    ProviderType type();

    boolean simulated();

    CheckoutSession createCheckout(ProviderContext ctx, CheckoutRequest request);

    Optional<ProviderPayment> findPayment(ProviderContext ctx, String providerPaymentId);

    Optional<ProviderPayment> findByExternalReference(ProviderContext ctx, UUID externalReference);

    ProviderRefund refund(ProviderContext ctx, String providerPaymentId, BigDecimal amount, UUID idempotencyKey);

    ConnectionTest testConnection(ProviderContext ctx);

    record ProviderContext(Long storeId, String storeSlug, String accessToken, String webhookSecret) {
    }

    record CheckoutRequest(UUID externalReference, UUID idempotencyKey, String description, BigDecimal amount,
                           String payerName, String payerEmail, String returnUrl, String notificationUrl,
                           Instant expiresAt) {
    }

    record CheckoutSession(String checkoutId, String checkoutUrl) {
    }

    record ProviderPayment(String providerPaymentId, String externalReference, PaymentStatus status, String rawStatus,
                           BigDecimal amount, String currency, BigDecimal refundedAmount, BigDecimal feeAmount,
                           String method, boolean liveMode) {
    }

    record ProviderRefund(String refundId, BigDecimal amount) {
    }

    record ConnectionTest(boolean ok, String message) {
    }

    class ProviderException extends RuntimeException {
        public ProviderException(String message) {
            super(message);
        }
    }
}
