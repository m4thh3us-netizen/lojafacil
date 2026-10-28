package com.lojafacil.orders;

public enum PaymentStatus {
    PENDING, APPROVED, DECLINED, EXPIRED, PARTIALLY_REFUNDED, REFUNDED;

    public boolean isPaid() {
        return this == APPROVED || this == PARTIALLY_REFUNDED;
    }
}
