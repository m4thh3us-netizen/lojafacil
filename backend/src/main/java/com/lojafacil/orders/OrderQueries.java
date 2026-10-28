package com.lojafacil.orders;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.auth.User;
import com.lojafacil.auth.UserRepository;
import com.lojafacil.orders.OrderDtos.HistoryView;
import com.lojafacil.orders.OrderDtos.ItemView;
import com.lojafacil.orders.OrderDtos.OrderDetail;
import com.lojafacil.orders.OrderDtos.OrderListItem;
import com.lojafacil.orders.OrderDtos.PaymentSummary;
import com.lojafacil.orders.OrderDtos.PublicOrderView;
import com.lojafacil.orders.OrderDtos.ReturnLine;
import com.lojafacil.orders.OrderDtos.ReturnView;
import com.lojafacil.payments.Payment;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.util.Tokens;
import com.lojafacil.shared.web.PageResponse;

/** Consultas de pedidos (painel e acompanhamento público). */
@Service
@Transactional(readOnly = true)
public class OrderQueries {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** Na linha do tempo pública aparecem só mudanças de estado (observações internas ficam de fora). */
    private static final Set<OrderHistory.Field> PUBLIC_FIELDS =
            Set.of(OrderHistory.Field.ORDER, OrderHistory.Field.PAYMENT, OrderHistory.Field.FULFILLMENT);

    public record Filter(String q, OrderStatus status, PaymentStatus paymentStatus, FulfillmentStatus fulfillmentStatus,
                         Order.Channel channel, LocalDate from, LocalDate to, boolean attentionOnly) {
    }

    private final OrderRepository orders;
    private final OrderHistory.Repo history;
    private final OrderReturn.Repo returns;
    private final Payment.Repo payments;
    private final UserRepository users;

    public OrderQueries(OrderRepository orders, OrderHistory.Repo history, OrderReturn.Repo returns,
                        Payment.Repo payments, UserRepository users) {
        this.orders = orders;
        this.history = history;
        this.returns = returns;
        this.payments = payments;
        this.users = users;
    }

    public PageResponse<OrderListItem> search(Long storeId, Filter f, Pageable pageable) {
        Specification<Order> spec = (root, q, cb) -> cb.equal(root.get("storeId"), storeId);
        if (f.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.paymentStatus() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("paymentStatus"), f.paymentStatus()));
        }
        if (f.fulfillmentStatus() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("fulfillmentStatus"), f.fulfillmentStatus()));
        }
        if (f.channel() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("channel"), f.channel()));
        }
        if (f.attentionOnly()) {
            spec = spec.and((root, q, cb) -> cb.isTrue(root.get("requiresAttention")));
        }
        if (f.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(ZONE).toInstant()));
        }
        if (f.to() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String term = f.q().trim().replace("#", "").toLowerCase(Locale.ROOT);
            String like = "%" + term + "%";
            Integer number = term.matches("\\d{1,9}") ? Integer.valueOf(term) : null;
            spec = spec.and((root, q, cb) -> number != null
                    ? cb.or(cb.equal(root.get("number"), number), cb.like(cb.lower(root.get("customerName")), like))
                    : cb.or(cb.like(cb.lower(root.get("customerName")), like), cb.like(cb.lower(root.get("customerEmail")), like)));
        }
        return PageResponse.of(orders.findAll(spec, pageable), OrderListItem::of);
    }

    public OrderDetail detail(Long storeId, Long orderId) {
        Order o = orders.findByIdAndStoreId(orderId, storeId).orElseThrow(() -> Errors.notFound("Pedido"));
        List<OrderHistory> hist = history.findByOrderIdOrderByCreatedAtAscIdAsc(o.getId());
        Map<Long, String> names = users.findAllById(hist.stream().map(OrderHistory::getUserId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        List<FulfillmentStatus> allowed = java.util.Arrays.stream(FulfillmentStatus.values())
                .filter(to -> to != FulfillmentStatus.RETURNED)
                .filter(to -> {
                    try {
                        OrderStateMachine.requireFulfillment(o, to);
                        return true;
                    } catch (RuntimeException e) {
                        return false;
                    }
                }).toList();
        return new OrderDetail(o.getId(), o.getNumber(), o.getCreatedAt(), o.getChannel(), o.getCustomerId(),
                o.getCustomerName(), o.getCustomerEmail(), o.getCustomerPhone(), o.getDeliveryMethod(),
                o.getDeliveryAddress(), o.getNotes(), o.getSubtotal(), o.getDiscountTotal(), o.getDeliveryFee(),
                o.getTotal(), o.getStatus(), o.getPaymentStatus(), o.getFulfillmentStatus(), o.isStockCommitted(),
                o.isRequiresAttention(), o.getAttentionReason(), o.getReservationExpiresAt(), o.getConfirmedAt(),
                o.getCancelledAt(), o.getCompletedAt(),
                o.getItems().stream().map(i -> ItemView.of(i, true)).toList(),
                payments.findByOrderIdOrderByCreatedAt(o.getId()).stream().map(OrderQueries::paymentSummary).toList(),
                returns.findByOrderIdOrderByCreatedAt(o.getId()).stream().map(OrderQueries::returnView).toList(),
                hist.stream().map(h -> historyView(h, h.getUserId() == null ? "Sistema" : names.getOrDefault(h.getUserId(), "—"))).toList(),
                allowed);
    }

    /** Acompanhamento pelo link com token: o número sequencial sozinho não dá acesso. */
    public PublicOrderView publicView(Long storeId, String token) {
        Order o = orders.findByTrackingTokenHash(Tokens.sha256(token))
                .filter(x -> x.getStoreId().equals(storeId))
                .orElseThrow(() -> Errors.notFound("Pedido"));
        Payment p = payments.findFirstByOrderIdOrderByCreatedAtDesc(o.getId()).orElse(null);
        boolean payable = o.getStatus() == OrderStatus.OPEN && !o.getPaymentStatus().isPaid();
        return new PublicOrderView(o.getNumber(), o.getCreatedAt(), o.getCustomerName(), o.getDeliveryMethod(),
                o.getDeliveryAddress(), o.getSubtotal(), o.getDiscountTotal(), o.getDeliveryFee(), o.getTotal(),
                o.getStatus(), o.getPaymentStatus(), o.getFulfillmentStatus(), o.getReservationExpiresAt(),
                payable && p != null ? p.getCheckoutUrl() : null, p != null && p.isSimulated(),
                o.getItems().stream().map(i -> ItemView.of(i, false)).toList(),
                history.findByOrderIdOrderByCreatedAtAscIdAsc(o.getId()).stream()
                        .filter(h -> PUBLIC_FIELDS.contains(h.getField()))
                        .map(h -> new HistoryView(h.getId(), h.getField(), h.getFromValue(), h.getToValue(), null, null, h.getCreatedAt()))
                        .toList());
    }

    static PaymentSummary paymentSummary(Payment p) {
        return new PaymentSummary(p.getId(), p.getProvider().name(), p.isSimulated(), p.getStatus().name(), p.getAmount(),
                p.getRefundedAmount(), p.getFeeAmount(), p.getMethod(), p.getCheckoutUrl(), p.getCreatedAt(), p.getUpdatedAt());
    }

    static ReturnView returnView(OrderReturn r) {
        return new ReturnView(r.getId(), r.getStatus(), r.getReason(), r.isRestocked(), r.getConditionOk(),
                r.getInspectionNote(), r.getCreatedAt(), r.getReceivedAt(),
                r.getItems().stream().map(i -> new ReturnLine(i.getOrderItem().getId(), i.getQuantity())).toList());
    }

    static HistoryView historyView(OrderHistory h, String userName) {
        return new HistoryView(h.getId(), h.getField(), h.getFromValue(), h.getToValue(), h.getNote(), userName, h.getCreatedAt());
    }
}
