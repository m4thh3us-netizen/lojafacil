package com.lojafacil.orders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.catalog.ProductVariant;
import com.lojafacil.catalog.VariantRepository;
import com.lojafacil.customers.Customer;
import com.lojafacil.customers.CustomerDtos.AddressData;
import com.lojafacil.customers.CustomerService;
import com.lojafacil.inventory.InventoryService;
import com.lojafacil.orders.OrderDtos.CartLine;
import com.lojafacil.orders.OrderDtos.ReceiveReturnRequest;
import com.lojafacil.orders.OrderDtos.ReturnLine;
import com.lojafacil.orders.OrderDtos.ReturnRequest;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.util.Tokens;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreRepository;

/**
 * Regras do ciclo de vida do pedido.
 *
 * Métodos marcados com Propagation.MANDATORY são etapas de um processo maior (checkout, pagamento,
 * expiração) e precisam rodar dentro da transação de quem os chama, recebendo o pedido já travado.
 */
@Service
public class OrderService {

    /** Dados necessários para criar um pedido, comuns à vitrine e ao pedido manual. */
    public record PlaceOrderCommand(Long storeId, Order.Channel channel, Long customerId, String name, String email,
                                    String phone, DeliveryMethod deliveryMethod, AddressData address,
                                    List<CartLine> items, String notes, boolean requireVisible) {
    }

    public record PlacedOrder(Order order, String trackingToken) {
    }

    private final OrderRepository orders;
    private final OrderHistory.Repo history;
    private final OrderReturn.Repo returns;
    private final StoreRepository stores;
    private final VariantRepository variants;
    private final InventoryService inventory;
    private final CustomerService customers;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public OrderService(OrderRepository orders, OrderHistory.Repo history, OrderReturn.Repo returns,
                        StoreRepository stores, VariantRepository variants, InventoryService inventory,
                        CustomerService customers, AuditService audit, CurrentUser currentUser) {
        this.orders = orders;
        this.history = history;
        this.returns = returns;
        this.stores = stores;
        this.variants = variants;
        this.inventory = inventory;
        this.customers = customers;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------ criação

    /**
     * Cria o pedido e reserva o estoque. Preços, descontos, entrega e total são calculados aqui
     * a partir do banco. Se faltar estoque, nada é gravado (a transação é desfeita).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PlacedOrder place(PlaceOrderCommand cmd) {
        Store store = stores.findById(cmd.storeId()).orElseThrow(() -> Errors.notFound("Loja"));
        if (cmd.deliveryMethod() == DeliveryMethod.LOCAL_DELIVERY && cmd.address() == null) {
            throw Errors.badRequest("ADDRESS_REQUIRED", "Informe o endereço de entrega.");
        }
        Map<Long, Integer> quantities = PricingService.aggregate(cmd.items());

        Customer customer = cmd.customerId() != null
                ? customers.findForOrder(store.getId(), cmd.customerId())
                : customers.findOrCreateForOrder(store.getId(), cmd.name(), cmd.email(), cmd.phone(), cmd.address());

        String token = Tokens.newToken();
        Order order = new Order();
        order.setStoreId(store.getId());
        order.setNumber(stores.nextOrderNumber(store.getId()));
        order.setTrackingTokenHash(Tokens.sha256(token));
        order.setChannel(cmd.channel());
        order.setCustomerId(customer.getId());
        order.setCustomerName(cmd.customerId() != null ? customer.getName() : cmd.name().trim());
        order.setCustomerEmail(cmd.customerId() != null ? customer.getEmail() : cmd.email().trim().toLowerCase());
        order.setCustomerPhone(cmd.customerId() != null ? customer.getPhone() : cmd.phone());
        order.setDeliveryMethod(cmd.deliveryMethod());
        order.setDeliveryAddress(cmd.deliveryMethod() == DeliveryMethod.LOCAL_DELIVERY ? cmd.address().formatted() : null);
        order.setDeliveryFee(PricingService.deliveryFee(store, cmd.deliveryMethod()));
        order.setNotes(cmd.notes() == null || cmd.notes().isBlank() ? null : cmd.notes().trim());
        order.setReservationExpiresAt(Instant.now().plus(store.getReservationMinutes(), ChronoUnit.MINUTES));
        order.setSubtotal(order.getDeliveryFee());
        order.setTotal(order.getDeliveryFee());
        orders.save(order);

        // Reserva com as variantes travadas; depois lê as mesmas entidades para montar os itens.
        inventory.reserve(store.getId(), order.getId(), quantities, order.getReservationExpiresAt());
        Map<Long, ProductVariant> locked = variants.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (cmd.requireVisible() && locked.values().stream().anyMatch(v -> !v.getProduct().isVisible())) {
            throw Errors.rule("PRODUCT_UNAVAILABLE", "Um dos produtos não está disponível na loja.");
        }
        PricingService.fillItems(order, quantities, locked);
        log(order, OrderHistory.Field.ORDER, null, OrderStatus.OPEN, cmd.channel() == Order.Channel.MANUAL
                ? "Pedido manual criado pela equipe" : "Pedido criado na loja virtual");
        return new PlacedOrder(order, token);
    }

    // ------------------------------------------------------------------ pagamento (chamado pelo módulo de pagamentos)

    /**
     * Pagamento aprovado. Converte a reserva em baixa (uma única vez) e confirma o pedido.
     * Trata o caso de pagamento após a expiração da reserva sem permitir estoque negativo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPaymentApproved(Order order, String note) {
        PaymentStatus before = order.getPaymentStatus();
        setPayment(order, PaymentStatus.APPROVED, note);
        if (order.isStockCommitted()) {
            return; // notificação repetida: estoque já baixado
        }
        Map<Long, Integer> quantities = quantitiesOf(order);

        if (order.getStatus() == OrderStatus.OPEN) {
            boolean committed = inventory.commitReservations(order.getStoreId(), order.getId())
                    || inventory.commitWithoutReservation(order.getStoreId(), order.getId(), quantities);
            if (committed) {
                order.setStockCommitted(true);
                confirm(order, "Pagamento aprovado");
            } else {
                order.flagAttention("Pagamento aprovado, mas sem estoque disponível. Reponha o estoque ou reembolse o cliente.");
                log(order, OrderHistory.Field.NOTE, null, null, order.getAttentionReason());
            }
        } else if (order.getStatus() == OrderStatus.CANCELLED) {
            // Pagamento tardio (reserva expirada ou pedido cancelado antes da aprovação)
            if (inventory.commitWithoutReservation(order.getStoreId(), order.getId(), quantities)) {
                order.setStockCommitted(true);
                order.setStatus(OrderStatus.CONFIRMED);
                order.setConfirmedAt(Instant.now());
                order.setCancelledAt(null);
                order.flagAttention("Pagamento aprovado após " + (before == PaymentStatus.EXPIRED ? "a expiração da reserva" : "o cancelamento")
                        + ". Havia estoque e o pedido foi reativado; confira se o atendimento deve prosseguir.");
                log(order, OrderHistory.Field.ORDER, OrderStatus.CANCELLED, OrderStatus.CONFIRMED, "Pedido reativado por pagamento tardio");
            } else {
                order.flagAttention("Pagamento aprovado após o pedido ser cancelado/expirado e sem estoque disponível. Reembolse o cliente.");
                log(order, OrderHistory.Field.NOTE, null, null, order.getAttentionReason());
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onPaymentStatus(Order order, PaymentStatus status, String note) {
        if (order.getPaymentStatus() == status && status != PaymentStatus.PARTIALLY_REFUNDED) {
            return;
        }
        setPayment(order, status, note);
    }

    /** Reserva expirada sem pagamento: libera o estoque e cancela o pedido. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expire(Order order) {
        if (order.getStatus() != OrderStatus.OPEN || order.getPaymentStatus().isPaid()) {
            return false;
        }
        inventory.releaseReservations(order.getStoreId(), order.getId());
        setPayment(order, PaymentStatus.EXPIRED, "Prazo de pagamento encerrado");
        setStatus(order, OrderStatus.CANCELLED, "Reserva de estoque expirada sem pagamento");
        return true;
    }

    /** Cancelamento de pedido ainda não pago. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelUnpaid(Order order, String reason) {
        if (order.getPaymentStatus().isPaid()) {
            throw Errors.rule("ORDER_PAID", "Pedido pago: o cancelamento exige reembolso.");
        }
        OrderStateMachine.requireOrder(order.getStatus(), OrderStatus.CANCELLED);
        inventory.releaseReservations(order.getStoreId(), order.getId());
        if (order.getPaymentStatus() == PaymentStatus.PENDING || order.getPaymentStatus() == PaymentStatus.DECLINED) {
            setPayment(order, PaymentStatus.EXPIRED, "Cobrança encerrada pelo cancelamento");
        }
        setStatus(order, OrderStatus.CANCELLED, reason);
        audit.record(order.getStoreId(), "ORDER", order.getId(), "ORDER_CANCELLED", "#" + order.getNumber() + ": " + reason);
    }

    /**
     * Cancelamento de pedido pago, depois do reembolso integral. Como os itens não saíram da loja
     * (atendimento antes do envio/retirada), eles retornam ao estoque.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelAfterFullRefund(Order order, String reason) {
        requireCancellableAfterPayment(order);
        OrderStateMachine.requireOrder(order.getStatus(), OrderStatus.CANCELLED);
        if (order.isStockCommitted()) {
            for (OrderItem item : order.getItems()) {
                inventory.restockReturn(order.getStoreId(), order.getId(), item.getVariantId(), item.getQuantity(),
                        "Cancelamento do pedido #" + order.getNumber() + " antes da entrega (itens não saíram da loja)");
            }
            order.setStockCommitted(false);
        } else {
            inventory.releaseReservations(order.getStoreId(), order.getId());
        }
        setStatus(order, OrderStatus.CANCELLED, reason);
        audit.record(order.getStoreId(), "ORDER", order.getId(), "ORDER_CANCELLED", "#" + order.getNumber() + " (reembolsado): " + reason);
    }

    public static void requireCancellableAfterPayment(Order order) {
        FulfillmentStatus f = order.getFulfillmentStatus();
        if (f == FulfillmentStatus.SHIPPED || f == FulfillmentStatus.DELIVERED || f == FulfillmentStatus.RETURNED) {
            throw Errors.rule("ALREADY_SHIPPED", "O pedido já foi enviado ou entregue. Registre uma devolução em vez de cancelar.");
        }
    }

    // ------------------------------------------------------------------ atendimento (painel)

    @Transactional
    public Order updateFulfillment(Long storeId, Long orderId, FulfillmentStatus to, String note) {
        Order order = lockForStore(storeId, orderId);
        if (to == FulfillmentStatus.RETURNED) {
            throw Errors.rule("USE_RETURN_FLOW", "Para marcar como devolvido, registre a devolução dos itens.");
        }
        OrderStateMachine.requireFulfillment(order, to);
        FulfillmentStatus from = order.getFulfillmentStatus();
        order.setFulfillmentStatus(to);
        log(order, OrderHistory.Field.FULFILLMENT, from, to, note);
        if (to == FulfillmentStatus.DELIVERED) {
            setStatus(order, OrderStatus.COMPLETED, "Pedido entregue");
            order.setCompletedAt(Instant.now());
        }
        audit.record(storeId, "ORDER", orderId, "FULFILLMENT_" + to, "#" + order.getNumber());
        return order;
    }

    @Transactional
    public void addNote(Long storeId, Long orderId, String note) {
        Order order = lockForStore(storeId, orderId);
        log(order, OrderHistory.Field.NOTE, null, null, note.trim());
    }

    @Transactional
    public void resolveAttention(Long storeId, Long orderId, String note) {
        Order order = lockForStore(storeId, orderId);
        order.setRequiresAttention(false);
        log(order, OrderHistory.Field.NOTE, null, null, "Pendência resolvida: " + note);
    }

    // ------------------------------------------------------------------ devoluções

    @Transactional
    public OrderReturn registerReturn(Long storeId, Long orderId, ReturnRequest req) {
        Order order = lockForStore(storeId, orderId);
        if (!order.isStockCommitted()) {
            throw Errors.rule("NOTHING_TO_RETURN", "Só é possível registrar devolução de pedidos com venda confirmada.");
        }
        if (order.getFulfillmentStatus() != FulfillmentStatus.DELIVERED && order.getFulfillmentStatus() != FulfillmentStatus.SHIPPED
                && order.getFulfillmentStatus() != FulfillmentStatus.RETURNED) {
            throw Errors.rule("NOT_DELIVERED", "Devoluções valem para pedidos enviados ou entregues. Para pedidos não enviados, use o cancelamento.");
        }
        Map<Long, OrderItem> itemsById = new HashMap<>();
        order.getItems().forEach(i -> itemsById.put(i.getId(), i));

        OrderReturn ret = new OrderReturn();
        ret.setStoreId(storeId);
        ret.setOrderId(orderId);
        ret.setReason(req.reason().trim());
        ret.setCreatedBy(currentUser.idOrNull());
        ret.setStatus(OrderReturn.Status.AWAITING_RECEIPT);
        for (ReturnLine line : req.items()) {
            OrderItem item = itemsById.get(line.orderItemId());
            if (item == null) {
                throw Errors.notFound("Item do pedido");
            }
            int already = returns.returnedQuantity(item.getId());
            if (already + line.quantity() > item.getQuantity()) {
                throw Errors.rule("RETURN_EXCEEDS", "Quantidade devolvida de \"" + item.getProductName()
                        + "\" excede o vendido (" + item.getQuantity() + ", já devolvido: " + already + ").");
            }
            OrderReturn.Item ri = new OrderReturn.Item();
            ri.setOrderReturn(ret);
            ri.setOrderItem(item);
            ri.setQuantity(line.quantity());
            ret.getItems().add(ri);
        }
        returns.save(ret);
        if (order.getFulfillmentStatus() != FulfillmentStatus.RETURNED) {
            FulfillmentStatus from = order.getFulfillmentStatus();
            order.setFulfillmentStatus(FulfillmentStatus.RETURNED);
            log(order, OrderHistory.Field.FULFILLMENT, from, FulfillmentStatus.RETURNED, "Devolução registrada: " + req.reason());
        }
        if (req.received()) {
            receive(order, ret, Boolean.TRUE.equals(req.conditionOk()), req.inspectionNote());
        } else {
            log(order, OrderHistory.Field.RETURN, null, "AWAITING_RECEIPT", "Aguardando recebimento dos itens devolvidos");
        }
        audit.record(storeId, "ORDER", orderId, "RETURN_REGISTERED", "#" + order.getNumber() + ": " + req.reason());
        return ret;
    }

    @Transactional
    public OrderReturn receiveReturn(Long storeId, Long orderId, Long returnId, ReceiveReturnRequest req) {
        Order order = lockForStore(storeId, orderId);
        OrderReturn ret = returns.findByIdAndOrderId(returnId, orderId).orElseThrow(() -> Errors.notFound("Devolução"));
        if (ret.getStatus() == OrderReturn.Status.RECEIVED) {
            throw Errors.rule("ALREADY_RECEIVED", "Esta devolução já foi recebida.");
        }
        receive(order, ret, req.conditionOk(), req.inspectionNote());
        return ret;
    }

    /** Recebimento conferido: só repõe estoque se o produto estiver em condições de revenda. */
    private void receive(Order order, OrderReturn ret, boolean conditionOk, String note) {
        ret.setStatus(OrderReturn.Status.RECEIVED);
        ret.setReceivedAt(Instant.now());
        ret.setReceivedBy(currentUser.idOrNull());
        ret.setConditionOk(conditionOk);
        ret.setInspectionNote(note);
        if (conditionOk) {
            for (OrderReturn.Item ri : ret.getItems()) {
                inventory.restockReturn(order.getStoreId(), order.getId(), ri.getOrderItem().getVariantId(), ri.getQuantity(),
                        "Devolução do pedido #" + order.getNumber() + " recebida e conferida");
            }
            ret.setRestocked(true);
        }
        log(order, OrderHistory.Field.RETURN, "AWAITING_RECEIPT", "RECEIVED",
                conditionOk ? "Itens recebidos em boas condições e devolvidos ao estoque"
                        : "Itens recebidos sem condições de revenda (estoque não reposto)" + (note != null ? ": " + note : ""));
    }

    // ------------------------------------------------------------------ auxiliares

    public Order lockForStore(Long storeId, Long orderId) {
        Order order = orders.lockById(orderId).orElseThrow(() -> Errors.notFound("Pedido"));
        if (!order.getStoreId().equals(storeId)) {
            throw Errors.notFound("Pedido"); // nunca revela pedidos de outra loja
        }
        return order;
    }

    private void confirm(Order order, String note) {
        setStatus(order, OrderStatus.CONFIRMED, note);
        order.setConfirmedAt(Instant.now());
    }

    private void setStatus(Order order, OrderStatus to, String note) {
        OrderStatus from = order.getStatus();
        OrderStateMachine.requireOrder(from, to);
        order.setStatus(to);
        if (to == OrderStatus.CANCELLED) {
            order.setCancelledAt(Instant.now());
        }
        log(order, OrderHistory.Field.ORDER, from, to, note);
    }

    private void setPayment(Order order, PaymentStatus to, String note) {
        PaymentStatus from = order.getPaymentStatus();
        if (from == to && to != PaymentStatus.PARTIALLY_REFUNDED) {
            return;
        }
        if (!OrderStateMachine.canPayment(from, to)) {
            throw Errors.rule("INVALID_TRANSITION", "Pagamento não pode ir de " + OrderStateMachine.label(from)
                    + " para " + OrderStateMachine.label(to) + ".");
        }
        order.setPaymentStatus(to);
        log(order, OrderHistory.Field.PAYMENT, from, to, note);
    }

    private void log(Order order, OrderHistory.Field field, Object from, Object to, String note) {
        history.save(new OrderHistory(order.getId(), currentUser.idOrNull(), field,
                from == null ? null : from.toString(), to == null ? null : to.toString(), note));
    }

    private static Map<Long, Integer> quantitiesOf(Order order) {
        Map<Long, Integer> q = new HashMap<>();
        order.getItems().forEach(i -> q.merge(i.getVariantId(), i.getQuantity(), Integer::sum));
        return q;
    }
}
