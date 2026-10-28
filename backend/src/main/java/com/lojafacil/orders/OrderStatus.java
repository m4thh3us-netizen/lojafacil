package com.lojafacil.orders;

/**
 * Situação comercial do pedido.
 * OPEN (aberto, aguardando pagamento) -> CONFIRMED (pago) -> COMPLETED (entregue);
 * OPEN ou CONFIRMED podem ir para CANCELLED conforme as regras de {@link OrderStateMachine}.
 */
public enum OrderStatus {
    OPEN, CONFIRMED, CANCELLED, COMPLETED
}
