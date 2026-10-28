package com.lojafacil.orders;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.lojafacil.shared.error.Errors;

public final class OrderStateMachine {
    private static final Map<OrderStatus, Set<OrderStatus>> ORDER = new EnumMap<>(OrderStatus.class);
    private static final Map<PaymentStatus, Set<PaymentStatus>> PAYMENT = new EnumMap<>(PaymentStatus.class);
    private static final Map<FulfillmentStatus, Set<FulfillmentStatus>> FULFILLMENT = new EnumMap<>(FulfillmentStatus.class);

    static {
        ORDER.put(OrderStatus.OPEN, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ORDER.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        ORDER.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ORDER.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

        PAYMENT.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.DECLINED, PaymentStatus.EXPIRED));

        PAYMENT.put(PaymentStatus.DECLINED, EnumSet.of(PaymentStatus.PENDING, PaymentStatus.APPROVED, PaymentStatus.EXPIRED));

        PAYMENT.put(PaymentStatus.EXPIRED, EnumSet.of(PaymentStatus.APPROVED));
        PAYMENT.put(PaymentStatus.APPROVED, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        PAYMENT.put(PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        PAYMENT.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));

        FULFILLMENT.put(FulfillmentStatus.AWAITING, EnumSet.of(FulfillmentStatus.PICKING));
        FULFILLMENT.put(FulfillmentStatus.PICKING, EnumSet.of(FulfillmentStatus.READY_FOR_PICKUP, FulfillmentStatus.SHIPPED, FulfillmentStatus.AWAITING));
        FULFILLMENT.put(FulfillmentStatus.READY_FOR_PICKUP, EnumSet.of(FulfillmentStatus.DELIVERED));
        FULFILLMENT.put(FulfillmentStatus.SHIPPED, EnumSet.of(FulfillmentStatus.DELIVERED));
        FULFILLMENT.put(FulfillmentStatus.DELIVERED, EnumSet.of(FulfillmentStatus.RETURNED));
        FULFILLMENT.put(FulfillmentStatus.RETURNED, EnumSet.noneOf(FulfillmentStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canOrder(OrderStatus from, OrderStatus to) {
        return ORDER.get(from).contains(to);
    }

    public static boolean canPayment(PaymentStatus from, PaymentStatus to) {
        return PAYMENT.get(from).contains(to);
    }

    public static boolean canFulfillment(FulfillmentStatus from, FulfillmentStatus to) {
        return FULFILLMENT.get(from).contains(to);
    }

    public static void requireOrder(OrderStatus from, OrderStatus to) {
        if (!canOrder(from, to)) {
            throw Errors.rule("INVALID_TRANSITION", "Não é possível mudar o pedido de " + label(from) + " para " + label(to) + ".");
        }
    }

    public static void requireFulfillment(Order order, FulfillmentStatus to) {
        FulfillmentStatus from = order.getFulfillmentStatus();
        if (!canFulfillment(from, to)) {
            throw Errors.rule("INVALID_TRANSITION", "Não é possível mudar o atendimento de " + label(from) + " para " + label(to) + ".");
        }
        if (to != FulfillmentStatus.RETURNED && order.getStatus() != OrderStatus.CONFIRMED) {
            throw Errors.rule("ORDER_NOT_CONFIRMED", "O atendimento só avança para pedidos confirmados (pagos).");
        }
        if (to == FulfillmentStatus.READY_FOR_PICKUP && order.getDeliveryMethod() != DeliveryMethod.PICKUP) {
            throw Errors.rule("INVALID_TRANSITION", "\"Pronto para retirada\" vale apenas para pedidos com retirada no local.");
        }
        if (to == FulfillmentStatus.SHIPPED && order.getDeliveryMethod() != DeliveryMethod.LOCAL_DELIVERY) {
            throw Errors.rule("INVALID_TRANSITION", "\"Enviado\" vale apenas para pedidos com entrega local.");
        }
    }

    public static String label(Enum<?> status) {
        return switch (status.name()) {
            case "OPEN" -> "Aberto";
            case "CONFIRMED" -> "Confirmado";
            case "CANCELLED" -> "Cancelado";
            case "COMPLETED" -> "Concluído";
            case "PENDING" -> "Pendente";
            case "APPROVED" -> "Aprovado";
            case "DECLINED" -> "Recusado";
            case "EXPIRED" -> "Expirado";
            case "PARTIALLY_REFUNDED" -> "Parcialmente reembolsado";
            case "REFUNDED" -> "Reembolsado";
            case "AWAITING" -> "Aguardando";
            case "PICKING" -> "Em separação";
            case "READY_FOR_PICKUP" -> "Pronto para retirada";
            case "SHIPPED" -> "Enviado";
            case "DELIVERED" -> "Entregue";
            case "RETURNED" -> "Devolvido";
            default -> status.name();
        };
    }
}
