package com.lojafacil.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.orders.OrderDtos.CancelRequest;
import com.lojafacil.orders.OrderDtos.CheckoutResponse;
import com.lojafacil.orders.OrderDtos.FulfillmentRequest;
import com.lojafacil.orders.OrderDtos.ManualOrderRequest;
import com.lojafacil.orders.OrderDtos.NoteRequest;
import com.lojafacil.orders.OrderDtos.OrderDetail;
import com.lojafacil.orders.OrderDtos.OrderListItem;
import com.lojafacil.orders.OrderDtos.Quote;
import com.lojafacil.orders.OrderDtos.QuoteRequest;
import com.lojafacil.orders.OrderDtos.ReceiveReturnRequest;
import com.lojafacil.orders.OrderDtos.ReturnRequest;
import com.lojafacil.payments.CheckoutService;
import com.lojafacil.payments.PaymentService;
import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.web.PageResponse;
import com.lojafacil.shared.web.Pages;
import com.lojafacil.store.StoreService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize(Access.OPERATIONS)
@Tag(name = "Pedidos")
public class OrderController {
    public record RefundRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
                                @NotBlank(message = "Informe o motivo.") @Size(max = 300) String reason) {
    }

    public record ManualPaymentRequest(@NotBlank(message = "Informe a forma de pagamento.") @Size(max = 40) String method) {
    }

    private final OrderQueries queries;
    private final OrderService orders;
    private final CheckoutService checkout;
    private final PaymentService payments;
    private final StoreService stores;
    private final CurrentUser currentUser;

    public OrderController(OrderQueries queries, OrderService orders, CheckoutService checkout, PaymentService payments,
                           StoreService stores, CurrentUser currentUser) {
        this.queries = queries;
        this.orders = orders;
        this.checkout = checkout;
        this.payments = payments;
        this.stores = stores;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<OrderListItem> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) PaymentStatus paymentStatus,
                                            @RequestParam(required = false) FulfillmentStatus fulfillmentStatus,
                                            @RequestParam(required = false) Order.Channel channel,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "false") boolean attentionOnly,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return queries.search(currentUser.storeId(),
                new OrderQueries.Filter(q, status, paymentStatus, fulfillmentStatus, channel, from, to, attentionOnly),
                Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @GetMapping("/{id}")
    public OrderDetail detail(@PathVariable Long id) {
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/quote")
    public Quote quote(@Valid @RequestBody QuoteRequest req) {
        return checkout.quote(stores.get(currentUser.storeId()).getSlug(), req);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse createManual(@Valid @RequestBody ManualOrderRequest req) {
        return checkout.manualOrder(currentUser.storeId(), req);
    }

    @PostMapping("/{id}/fulfillment")
    public OrderDetail fulfillment(@PathVariable Long id, @Valid @RequestBody FulfillmentRequest req) {
        orders.updateFulfillment(currentUser.storeId(), id, req.status(), req.note());
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/notes")
    public OrderDetail note(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        orders.addNote(currentUser.storeId(), id, req.note());
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/attention/resolve")
    public OrderDetail resolveAttention(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        orders.resolveAttention(currentUser.storeId(), id, req.note());
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(Access.MANAGEMENT)
    public OrderDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        payments.cancelOrder(currentUser.storeId(), id, req.reason());
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/refunds")
    @PreAuthorize(Access.MANAGEMENT)
    public OrderDetail refund(@PathVariable Long id, @Valid @RequestBody RefundRequest req) {
        payments.refund(currentUser.storeId(), id, req.amount(), req.reason());
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/payments/manual")
    public OrderDetail manualPayment(@PathVariable Long id, @Valid @RequestBody ManualPaymentRequest req) {
        payments.registerManualPayment(currentUser.storeId(), id, req.method());
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/payments/sync")
    public OrderDetail syncPayment(@PathVariable Long id) {
        payments.syncOrder(currentUser.storeId(), id);
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/returns")
    public OrderDetail registerReturn(@PathVariable Long id, @Valid @RequestBody ReturnRequest req) {
        orders.registerReturn(currentUser.storeId(), id, req);
        return queries.detail(currentUser.storeId(), id);
    }

    @PostMapping("/{id}/returns/{returnId}/receive")
    public OrderDetail receiveReturn(@PathVariable Long id, @PathVariable Long returnId,
                                     @Valid @RequestBody ReceiveReturnRequest req) {
        orders.receiveReturn(currentUser.storeId(), id, returnId, req);
        return queries.detail(currentUser.storeId(), id);
    }
}
