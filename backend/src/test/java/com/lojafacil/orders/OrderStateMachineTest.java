package com.lojafacil.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.lojafacil.shared.error.ApiException;

class OrderStateMachineTest {
    @Test
    void pedidoSegueCaminhosValidos() {
        assertThat(OrderStateMachine.canOrder(OrderStatus.OPEN, OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStateMachine.canOrder(OrderStatus.CONFIRMED, OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStateMachine.canOrder(OrderStatus.OPEN, OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStateMachine.canOrder(OrderStatus.CANCELLED, OrderStatus.OPEN)).isFalse();
        assertThat(OrderStateMachine.canOrder(OrderStatus.COMPLETED, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void pagamentoAprovadoNuncaVoltaParaPendente() {
        assertThat(OrderStateMachine.canPayment(PaymentStatus.APPROVED, PaymentStatus.PENDING)).isFalse();
        assertThat(OrderStateMachine.canPayment(PaymentStatus.APPROVED, PaymentStatus.DECLINED)).isFalse();
        assertThat(OrderStateMachine.canPayment(PaymentStatus.REFUNDED, PaymentStatus.APPROVED)).isFalse();
        assertThat(OrderStateMachine.canPayment(PaymentStatus.EXPIRED, PaymentStatus.APPROVED)).isTrue();
        assertThat(OrderStateMachine.canPayment(PaymentStatus.APPROVED, PaymentStatus.PARTIALLY_REFUNDED)).isTrue();
    }

    @Test
    void atendimentoExigePedidoPago() {
        Order o = order(OrderStatus.OPEN, DeliveryMethod.PICKUP, FulfillmentStatus.AWAITING);
        assertThatThrownBy(() -> OrderStateMachine.requireFulfillment(o, FulfillmentStatus.PICKING))
                .isInstanceOf(ApiException.class).hasMessageContaining("confirmados");
    }

    @Test
    void retiradaEEntregaTemCaminhosProprios() {
        Order pickup = order(OrderStatus.CONFIRMED, DeliveryMethod.PICKUP, FulfillmentStatus.PICKING);
        OrderStateMachine.requireFulfillment(pickup, FulfillmentStatus.READY_FOR_PICKUP);
        assertThatThrownBy(() -> OrderStateMachine.requireFulfillment(pickup, FulfillmentStatus.SHIPPED))
                .isInstanceOf(ApiException.class);

        Order delivery = order(OrderStatus.CONFIRMED, DeliveryMethod.LOCAL_DELIVERY, FulfillmentStatus.PICKING);
        OrderStateMachine.requireFulfillment(delivery, FulfillmentStatus.SHIPPED);
        assertThatThrownBy(() -> OrderStateMachine.requireFulfillment(delivery, FulfillmentStatus.READY_FOR_PICKUP))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void naoPulaEtapas() {
        Order o = order(OrderStatus.CONFIRMED, DeliveryMethod.PICKUP, FulfillmentStatus.AWAITING);
        assertThatThrownBy(() -> OrderStateMachine.requireFulfillment(o, FulfillmentStatus.DELIVERED))
                .isInstanceOf(ApiException.class);
    }

    private static Order order(OrderStatus s, DeliveryMethod dm, FulfillmentStatus f) {
        Order o = new Order();
        o.setStatus(s);
        o.setDeliveryMethod(dm);
        o.setFulfillmentStatus(f);
        return o;
    }
}
