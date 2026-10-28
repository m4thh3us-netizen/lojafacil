package com.lojafacil.orders;

/** Situação financeira do pagamento do pedido (independente da situação comercial e do atendimento). */
public enum PaymentStatus {
    PENDING, APPROVED, DECLINED, EXPIRED, PARTIALLY_REFUNDED, REFUNDED;

    public boolean isPaid() {
        return this == APPROVED || this == PARTIALLY_REFUNDED;
    }
}
