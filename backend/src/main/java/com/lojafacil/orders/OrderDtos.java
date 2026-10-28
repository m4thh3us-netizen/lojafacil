package com.lojafacil.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.lojafacil.customers.CustomerDtos.AddressData;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record CartLine(@NotNull Long variantId, @Min(value = 1, message = "Quantidade mínima: 1.") @Max(999) int quantity) {
    }

    public record CustomerData(
            @NotBlank(message = "Informe seu nome.") @Size(max = 120) String name,
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @NotBlank(message = "Informe seu telefone.") @Size(min = 8, max = 30, message = "Telefone inválido.") String phone) {
    }

    public record CheckoutRequest(
            @NotNull @Valid CustomerData customer,
            @NotNull DeliveryMethod deliveryMethod,
            @Valid AddressData address,
            @NotEmpty(message = "O carrinho está vazio.") @Size(max = 50) @Valid List<CartLine> items,
            @Size(max = 500) String notes) {
    }

    public record QuoteRequest(@NotNull DeliveryMethod deliveryMethod,
                               @NotEmpty @Size(max = 50) @Valid List<CartLine> items) {
    }

    public enum ManualPaymentMode { LINK, OFFLINE }

    public record ManualOrderRequest(
            Long customerId,
            @Valid CustomerData customer,
            @NotNull DeliveryMethod deliveryMethod,
            @Valid AddressData address,
            @NotEmpty @Size(max = 50) @Valid List<CartLine> items,
            @NotNull ManualPaymentMode paymentMode,
            @Size(max = 500) String notes) {
    }

    public record FulfillmentRequest(@NotNull FulfillmentStatus status, @Size(max = 300) String note) {
    }

    public record NoteRequest(@NotBlank @Size(max = 500) String note) {
    }

    public record CancelRequest(@NotBlank(message = "Informe o motivo do cancelamento.") @Size(max = 300) String reason) {
    }

    public record ReturnLine(@NotNull Long orderItemId, @Min(1) int quantity) {
    }

    public record ReturnRequest(
            @NotEmpty @Valid List<ReturnLine> items,
            @NotBlank(message = "Informe o motivo.") @Size(max = 500) String reason,

            boolean received,

            Boolean conditionOk,
            @Size(max = 500) String inspectionNote) {
    }

    public record ReceiveReturnRequest(@NotNull Boolean conditionOk, @Size(max = 500) String inspectionNote) {
    }

    public record QuoteLine(Long variantId, String productName, String variantName, String sku, String imageUrl,
                            int quantity, BigDecimal unitPrice, BigDecimal unitDiscount, BigDecimal lineTotal,
                            boolean available, int availableQty, String problem) {
    }

    public record Quote(List<QuoteLine> items, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal deliveryFee,
                        BigDecimal total, boolean valid) {
    }

    public record ItemView(Long id, Long productId, Long variantId, String productName, String variantName, String sku,
                           int quantity, BigDecimal unitPrice, BigDecimal unitDiscount, BigDecimal lineTotal, BigDecimal unitCost) {
        static ItemView of(OrderItem i, boolean includeCost) {
            return new ItemView(i.getId(), i.getProductId(), i.getVariantId(), i.getProductName(), i.getVariantName(), i.getSku(),
                    i.getQuantity(), i.getUnitPrice(), i.getUnitDiscount(), i.getLineTotal(), includeCost ? i.getUnitCost() : null);
        }
    }

    public record HistoryView(Long id, OrderHistory.Field field, String fromValue, String toValue, String note,
                              String userName, Instant createdAt) {
    }

    public record ReturnView(Long id, OrderReturn.Status status, String reason, boolean restocked, Boolean conditionOk,
                             String inspectionNote, Instant createdAt, Instant receivedAt, List<ReturnLine> items) {
    }

    public record PaymentSummary(Long id, String provider, boolean simulated, String status, BigDecimal amount,
                                 BigDecimal refundedAmount, BigDecimal feeAmount, String method, String checkoutUrl,
                                 Instant createdAt, Instant updatedAt) {
    }

    public record OrderListItem(Long id, int number, Instant createdAt, String customerName, Order.Channel channel,
                                DeliveryMethod deliveryMethod, BigDecimal total, OrderStatus status,
                                PaymentStatus paymentStatus, FulfillmentStatus fulfillmentStatus,
                                boolean requiresAttention, int itemCount) {
        static OrderListItem of(Order o) {
            return new OrderListItem(o.getId(), o.getNumber(), o.getCreatedAt(), o.getCustomerName(), o.getChannel(),
                    o.getDeliveryMethod(), o.getTotal(), o.getStatus(), o.getPaymentStatus(), o.getFulfillmentStatus(),
                    o.isRequiresAttention(), o.getItems().stream().mapToInt(OrderItem::getQuantity).sum());
        }
    }

    public record OrderDetail(Long id, int number, Instant createdAt, Order.Channel channel, Long customerId,
                              String customerName, String customerEmail, String customerPhone,
                              DeliveryMethod deliveryMethod, String deliveryAddress, String notes,
                              BigDecimal subtotal, BigDecimal discountTotal, BigDecimal deliveryFee, BigDecimal total,
                              OrderStatus status, PaymentStatus paymentStatus, FulfillmentStatus fulfillmentStatus,
                              boolean stockCommitted, boolean requiresAttention, String attentionReason,
                              Instant reservationExpiresAt, Instant confirmedAt, Instant cancelledAt, Instant completedAt,
                              List<ItemView> items, List<PaymentSummary> payments, List<ReturnView> returns,
                              List<HistoryView> history, List<FulfillmentStatus> allowedFulfillment) {
    }

    public record CheckoutResponse(int orderNumber, String trackingToken, String trackingUrl, String checkoutUrl,
                                   boolean simulated, BigDecimal total, String paymentError) {
    }

    public record PublicOrderView(int number, Instant createdAt, String customerName, DeliveryMethod deliveryMethod,
                                  String deliveryAddress, BigDecimal subtotal, BigDecimal discountTotal,
                                  BigDecimal deliveryFee, BigDecimal total, OrderStatus status,
                                  PaymentStatus paymentStatus, FulfillmentStatus fulfillmentStatus,
                                  Instant reservationExpiresAt, String checkoutUrl, boolean simulated,
                                  List<ItemView> items, List<HistoryView> timeline) {
    }
}
