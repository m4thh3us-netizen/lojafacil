package com.lojafacil.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FinanceDtos {
    private FinanceDtos() {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @NotNull EntryType type, boolean active) {
    }

    public record CategoryView(Long id, String name, EntryType type, String systemKey, boolean active) {
        static CategoryView of(FinancialCategory c) {
            return new CategoryView(c.getId(), c.getName(), c.getType(), c.getSystemKey() == null ? null : c.getSystemKey().name(), c.isActive());
        }
    }

    public record EntryRequest(
            @NotNull EntryType type,
            @NotNull(message = "Escolha a categoria.") Long categoryId,
            @NotBlank(message = "Informe a descrição.") @Size(max = 200) String description,
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor deve ser positivo.")
            @Digits(integer = 12, fraction = 2) BigDecimal totalAmount,
            @Min(1) @Max(60) int installments,
            @NotNull(message = "Informe o vencimento.") LocalDate firstDueDate,

            boolean paid,
            LocalDate paidDate,
            @Size(max = 160) String counterparty,
            Long supplierId,
            Long customerId,
            @Size(max = 500) String notes) {
    }

    public record SettleRequest(@NotNull LocalDate paidDate) {
    }

    public record ReasonRequest(@NotBlank(message = "Informe o motivo.") @Size(max = 300) String reason) {
    }

    public record EntryView(Long id, EntryType type, Long categoryId, String categoryName, String description,
                            BigDecimal amount, LocalDate dueDate, LocalDate paidDate, FinancialEntry.Status status,
                            boolean overdue, UUID installmentGroup, Integer installmentNumber, Integer installmentCount,
                            String counterparty, Long orderId, Long paymentId, FinancialEntry.SourceType sourceType,
                            Long reversalOfId, boolean reversed, String notes, Instant createdAt) {
        static EntryView of(FinancialEntry e, LocalDate today) {
            return new EntryView(e.getId(), e.getType(), e.getCategory().getId(), e.getCategory().getName(),
                    e.getDescription(), e.getAmount(), e.getDueDate(), e.getPaidDate(), e.getStatus(),
                    e.getStatus() == FinancialEntry.Status.PENDING && e.getDueDate().isBefore(today),
                    e.getInstallmentGroup(), e.getInstallmentNumber(), e.getInstallmentCount(), e.getCounterparty(),
                    e.getOrderId(), e.getPaymentId(), e.getSourceType(), e.getReversalOfId(), e.isReversed(),
                    e.getNotes(), e.getCreatedAt());
        }
    }

    public record EntryTotals(BigDecimal income, BigDecimal expense, BigDecimal balance) {
    }

    public record CashFlowPoint(LocalDate date, BigDecimal realizedIn, BigDecimal realizedOut,
                                BigDecimal forecastIn, BigDecimal forecastOut, BigDecimal realizedBalance,
                                BigDecimal projectedBalance) {
    }

    public record CashFlow(LocalDate from, LocalDate to, String granularity, BigDecimal openingBalance,
                           List<CashFlowPoint> points) {
    }

    public record Summary(LocalDate from, LocalDate to,
                          BigDecimal salesRevenue, long salesCount,
                          BigDecimal receipts, BigDecimal receivablesOpen, BigDecimal receivablesOverdue,
                          BigDecimal payablesOpen, BigDecimal payablesOverdue,
                          BigDecimal expensesPaid, BigDecimal operatingExpenses,
                          BigDecimal fees, BigDecimal refunds, BigDecimal costOfGoodsSold,
                          BigDecimal estimatedResult) {
    }
}
