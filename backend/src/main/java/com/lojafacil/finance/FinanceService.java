package com.lojafacil.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.auth.StoreCreatedEvent;
import com.lojafacil.finance.FinanceDtos.CashFlow;
import com.lojafacil.finance.FinanceDtos.CashFlowPoint;
import com.lojafacil.finance.FinanceDtos.CategoryRequest;
import com.lojafacil.finance.FinanceDtos.CategoryView;
import com.lojafacil.finance.FinanceDtos.EntryRequest;
import com.lojafacil.finance.FinanceDtos.EntryView;
import com.lojafacil.finance.FinanceDtos.Summary;
import com.lojafacil.finance.FinancialCategory.SystemKey;
import com.lojafacil.finance.FinancialEntry.SourceType;
import com.lojafacil.finance.FinancialEntry.Status;
import com.lojafacil.orders.OrderRepository;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.money.Money;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.web.PageResponse;

@Service
public class FinanceService {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final FinancialCategory.Repo categories;
    private final FinancialEntryRepository entries;
    private final OrderRepository orders;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public FinanceService(FinancialCategory.Repo categories, FinancialEntryRepository entries, OrderRepository orders,
                          AuditService audit, CurrentUser currentUser) {
        this.categories = categories;
        this.entries = entries;
        this.orders = orders;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------ categorias

    /** Categorias padrão criadas junto com cada loja nova. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStoreCreated(StoreCreatedEvent event) {
        Long s = event.storeId();
        category(s, "Vendas", EntryType.INCOME, SystemKey.SALES);
        category(s, "Outras receitas", EntryType.INCOME, null);
        category(s, "Taxas de pagamento", EntryType.EXPENSE, SystemKey.FEES);
        category(s, "Reembolsos a clientes", EntryType.EXPENSE, SystemKey.REFUNDS);
        category(s, "Compras de mercadorias", EntryType.EXPENSE, null);
        category(s, "Aluguel", EntryType.EXPENSE, null);
        category(s, "Salários e pró-labore", EntryType.EXPENSE, null);
        category(s, "Marketing", EntryType.EXPENSE, null);
        category(s, "Energia, água e internet", EntryType.EXPENSE, null);
        category(s, "Outras despesas", EntryType.EXPENSE, null);
    }

    private void category(Long storeId, String name, EntryType type, SystemKey key) {
        FinancialCategory c = new FinancialCategory();
        c.setStoreId(storeId);
        c.setName(name);
        c.setType(type);
        c.setSystemKey(key);
        categories.save(c);
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories(Long storeId) {
        return categories.findByStoreIdOrderByTypeAscNameAsc(storeId).stream().map(CategoryView::of).toList();
    }

    @Transactional
    public CategoryView saveCategory(Long storeId, Long id, CategoryRequest req) {
        FinancialCategory c;
        if (id == null) {
            if (categories.existsByStoreIdAndTypeAndNameIgnoreCase(storeId, req.type(), req.name().trim())) {
                throw Errors.conflict("CATEGORY_EXISTS", "Já existe uma categoria com este nome.");
            }
            c = new FinancialCategory();
            c.setStoreId(storeId);
            c.setType(req.type());
        } else {
            c = categories.findByIdAndStoreId(id, storeId).orElseThrow(() -> Errors.notFound("Categoria"));
            if (c.getSystemKey() != null && !req.active()) {
                throw Errors.rule("SYSTEM_CATEGORY", "Categorias usadas pelas integrações não podem ser desativadas.");
            }
        }
        c.setName(req.name().trim());
        c.setActive(req.active());
        return CategoryView.of(categories.save(c));
    }

    // ------------------------------------------------------------------ lançamentos manuais

    @Transactional
    public List<EntryView> create(Long storeId, EntryRequest req) {
        FinancialCategory cat = categories.findByIdAndStoreId(req.categoryId(), storeId)
                .orElseThrow(() -> Errors.notFound("Categoria"));
        if (cat.getType() != req.type()) {
            throw Errors.badRequest("CATEGORY_TYPE", "A categoria escolhida não é do tipo " + (req.type() == EntryType.INCOME ? "receita." : "despesa."));
        }
        if (cat.getSystemKey() != null) {
            throw Errors.rule("SYSTEM_CATEGORY", "Esta categoria é preenchida automaticamente pelas vendas e pagamentos.");
        }
        if (req.paid() && req.installments() > 1) {
            throw Errors.badRequest("PAID_INSTALLMENTS", "Lançamentos parcelados são criados como pendentes; dê baixa em cada parcela.");
        }
        List<BigDecimal> parts = Money.splitInstallments(req.totalAmount(), req.installments());
        UUID group = req.installments() > 1 ? UUID.randomUUID() : null;
        List<EntryView> created = new ArrayList<>();
        LocalDate today = today();
        for (int i = 0; i < parts.size(); i++) {
            FinancialEntry e = new FinancialEntry();
            e.setStoreId(storeId);
            e.setCategory(cat);
            e.setType(req.type());
            e.setDescription(req.description().trim() + (group != null ? " (" + (i + 1) + "/" + parts.size() + ")" : ""));
            e.setAmount(parts.get(i));
            e.setDueDate(req.firstDueDate().plusMonths(i));
            e.setInstallmentGroup(group);
            e.setInstallmentNumber(group != null ? i + 1 : null);
            e.setInstallmentCount(group != null ? parts.size() : null);
            e.setCounterparty(blankToNull(req.counterparty()));
            e.setSupplierId(req.supplierId());
            e.setCustomerId(req.customerId());
            e.setNotes(blankToNull(req.notes()));
            e.setCreatedBy(currentUser.idOrNull());
            if (req.paid()) {
                e.setStatus(Status.PAID);
                e.setPaidDate(req.paidDate() != null ? req.paidDate() : today);
            }
            entries.save(e);
            created.add(EntryView.of(e, today));
        }
        audit.record(storeId, "FINANCE", created.get(0).id(), "ENTRY_CREATED",
                req.type() + " " + req.totalAmount() + " em " + req.installments() + "x - " + req.description());
        return created;
    }

    /** Baixa: marca uma conta pendente como paga/recebida. */
    @Transactional
    public EntryView settle(Long storeId, Long id, LocalDate paidDate) {
        FinancialEntry e = find(storeId, id);
        if (e.getStatus() != Status.PENDING) {
            throw Errors.rule("NOT_PENDING", "Apenas lançamentos pendentes podem receber baixa.");
        }
        if (paidDate.isAfter(today())) {
            throw Errors.badRequest("FUTURE_DATE", "A data de pagamento não pode ser futura.");
        }
        e.setStatus(Status.PAID);
        e.setPaidDate(paidDate);
        audit.record(storeId, "FINANCE", id, "ENTRY_SETTLED", e.getDescription() + " " + e.getAmount() + " em " + paidDate);
        return EntryView.of(e, today());
    }

    @Transactional
    public EntryView cancel(Long storeId, Long id, String reason) {
        FinancialEntry e = find(storeId, id);
        if (e.getStatus() != Status.PENDING) {
            throw Errors.rule("NOT_PENDING", "Lançamentos já realizados não são cancelados: use o estorno.");
        }
        if (e.getSourceType() != SourceType.MANUAL) {
            throw Errors.rule("INTEGRATION_ENTRY", "Lançamentos gerados por pedidos são ajustados pelo próprio pedido.");
        }
        e.setStatus(Status.CANCELLED);
        e.setNotes(appendNote(e.getNotes(), "Cancelado: " + reason));
        audit.record(storeId, "FINANCE", id, "ENTRY_CANCELLED", reason);
        return EntryView.of(e, today());
    }

    /**
     * Estorno de lançamento realizado: cria um lançamento de tipo oposto, mesma categoria e valor,
     * vinculado ao original. O original permanece no histórico, marcado como estornado.
     */
    @Transactional
    public EntryView reverse(Long storeId, Long id, String reason) {
        FinancialEntry original = find(storeId, id);
        if (original.getStatus() != Status.PAID) {
            throw Errors.rule("NOT_PAID", "Só é possível estornar lançamentos realizados. Pendentes podem ser cancelados.");
        }
        if (original.isReversed() || original.getSourceType() == SourceType.REVERSAL) {
            throw Errors.rule("ALREADY_REVERSED", "Este lançamento já foi estornado ou é um estorno.");
        }
        if (original.getSourceType() != SourceType.MANUAL) {
            throw Errors.rule("INTEGRATION_ENTRY", "Lançamentos de vendas são corrigidos por reembolso no pedido.");
        }
        FinancialEntry rev = new FinancialEntry();
        rev.setStoreId(storeId);
        rev.setCategory(original.getCategory());
        rev.setType(original.getType().opposite());
        rev.setDescription("Estorno: " + original.getDescription());
        rev.setAmount(original.getAmount());
        rev.setDueDate(today());
        rev.setPaidDate(today());
        rev.setStatus(Status.PAID);
        rev.setSourceType(SourceType.REVERSAL);
        rev.setSourceRef("reversal:" + original.getId());
        rev.setReversalOfId(original.getId());
        rev.setCounterparty(original.getCounterparty());
        rev.setNotes(reason);
        rev.setCreatedBy(currentUser.idOrNull());
        entries.save(rev);
        original.setReversed(true);
        audit.record(storeId, "FINANCE", original.getId(), "ENTRY_REVERSED", reason);
        return EntryView.of(rev, today());
    }

    // ------------------------------------------------------------------ lançamentos automáticos (integrações)

    /**
     * Lançamento gerado por pedido/pagamento. É idempotente: o par (sourceType, sourceRef) é único,
     * então reprocessar a mesma notificação não duplica valores.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordIntegration(Long storeId, EntryType type, SystemKey key, BigDecimal amount, LocalDate date,
                                  String description, SourceType source, String sourceRef, Long orderId,
                                  Long paymentId, Long customerId) {
        if (amount == null || amount.signum() <= 0 || entries.existsByStoreIdAndSourceTypeAndSourceRef(storeId, source, sourceRef)) {
            return;
        }
        FinancialCategory cat = categories.findByStoreIdAndSystemKey(storeId, key)
                .orElseThrow(() -> new IllegalStateException("Categoria de sistema ausente: " + key));
        FinancialEntry e = new FinancialEntry();
        e.setStoreId(storeId);
        e.setCategory(cat);
        e.setType(type);
        e.setDescription(description);
        e.setAmount(Money.normalize(amount));
        e.setDueDate(date);
        e.setPaidDate(date);
        e.setStatus(Status.PAID);
        e.setSourceType(source);
        e.setSourceRef(sourceRef);
        e.setOrderId(orderId);
        e.setPaymentId(paymentId);
        e.setCustomerId(customerId);
        entries.save(e);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public PageResponse<EntryView> search(Long storeId, EntryType type, Status status, Long categoryId, LocalDate from,
                                          LocalDate to, String dateField, boolean overdueOnly, String q, Pageable pageable) {
        LocalDate today = today();
        Specification<FinancialEntry> spec = (root, query, cb) -> cb.equal(root.get("storeId"), storeId);
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        String field = "paid".equals(dateField) ? "paidDate" : "dueDate";
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), to));
        }
        if (overdueOnly) {
            spec = spec.and((root, query, cb) -> cb.and(cb.equal(root.get("status"), Status.PENDING), cb.lessThan(root.get("dueDate"), today)));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("counterparty"), "")), like)));
        }
        Page<FinancialEntry> page = entries.findAll(spec, pageable);
        return PageResponse.of(page, e -> EntryView.of(e, today));
    }

    /**
     * Fluxo de caixa: realizado (por data de pagamento) e previsto (pendentes por vencimento).
     * Saldo projetado = saldo realizado + previstos até a data.
     */
    @Transactional(readOnly = true)
    public CashFlow cashFlow(Long storeId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 730) {
            throw Errors.badRequest("INVALID_PERIOD", "Período inválido (máximo de 2 anos).");
        }
        boolean monthly = ChronoUnit.DAYS.between(from, to) > 62;
        Map<LocalDate, BigDecimal[]> buckets = new TreeMap<>();
        for (LocalDate d = bucket(from, monthly); !d.isAfter(to); d = monthly ? d.plusMonths(1) : d.plusDays(1)) {
            buckets.put(d, new BigDecimal[]{Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO});
        }
        for (Object[] row : entries.realizedByDay(storeId, from, to)) {
            BigDecimal[] b = buckets.get(bucket((LocalDate) row[0], monthly));
            int idx = row[1] == EntryType.INCOME ? 0 : 1;
            b[idx] = b[idx].add((BigDecimal) row[2]);
        }
        // pendentes vencidos antes do período entram no primeiro dia como previsão em atraso
        for (Object[] row : entries.forecastByDay(storeId, LocalDate.of(2000, 1, 1), to)) {
            LocalDate d = (LocalDate) row[0];
            BigDecimal[] b = buckets.get(bucket(d.isBefore(from) ? from : d, monthly));
            int idx = row[1] == EntryType.INCOME ? 2 : 3;
            b[idx] = b[idx].add((BigDecimal) row[2]);
        }
        BigDecimal opening = Money.normalize(entries.realizedBalanceBefore(storeId, from));
        BigDecimal realized = opening;
        BigDecimal projected = opening;
        List<CashFlowPoint> points = new ArrayList<>();
        for (var e : buckets.entrySet()) {
            BigDecimal[] b = e.getValue();
            realized = realized.add(b[0]).subtract(b[1]);
            projected = projected.add(b[0]).subtract(b[1]).add(b[2]).subtract(b[3]);
            points.add(new CashFlowPoint(e.getKey(), b[0], b[1], b[2], b[3], realized, projected));
        }
        return new CashFlow(from, to, monthly ? "month" : "day", opening, points);
    }

    @Transactional(readOnly = true)
    public Summary summary(Long storeId, LocalDate from, LocalDate to) {
        var start = from.atStartOfDay(ZONE).toInstant();
        var end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        BigDecimal sales = Money.normalize(orders.salesTotal(storeId, start, end));
        long salesCount = orders.salesCount(storeId, start, end);
        BigDecimal cogs = Money.normalize(orders.costOfGoodsSold(storeId, start, end));

        BigDecimal incomeNonReversal = Money.ZERO;
        BigDecimal incomeReversals = Money.ZERO;   // estornos de despesas
        BigDecimal expenseManual = Money.ZERO;
        BigDecimal expenseReversals = Money.ZERO;  // estornos de receitas
        BigDecimal fees = Money.ZERO;
        BigDecimal refunds = Money.ZERO;
        for (Object[] row : entries.paidBreakdown(storeId, from, to)) {
            EntryType type = (EntryType) row[0];
            SourceType source = (SourceType) row[1];
            BigDecimal amount = (BigDecimal) row[3];
            if (type == EntryType.INCOME) {
                if (source == SourceType.REVERSAL) {
                    incomeReversals = incomeReversals.add(amount);
                } else {
                    incomeNonReversal = incomeNonReversal.add(amount);
                }
            } else {
                switch (source) {
                    case PAYMENT_FEE -> fees = fees.add(amount);
                    case REFUND -> refunds = refunds.add(amount);
                    case REVERSAL -> expenseReversals = expenseReversals.add(amount);
                    default -> expenseManual = expenseManual.add(amount);
                }
            }
        }
        BigDecimal receipts = incomeNonReversal.subtract(expenseReversals);
        BigDecimal expensesPaid = expenseManual.subtract(incomeReversals);
        BigDecimal operating = Money.normalize(entries.operatingExpensesByDue(storeId, from, to))
                .subtract(Money.normalize(entries.expenseReversalsByDue(storeId, from, to)));
        LocalDate today = today();
        BigDecimal result = sales.subtract(cogs).subtract(fees).subtract(refunds).subtract(operating);
        return new Summary(from, to, sales, salesCount, receipts,
                Money.normalize(entries.openTotal(storeId, EntryType.INCOME)),
                Money.normalize(entries.overdueTotal(storeId, EntryType.INCOME, today)),
                Money.normalize(entries.openTotal(storeId, EntryType.EXPENSE)),
                Money.normalize(entries.overdueTotal(storeId, EntryType.EXPENSE, today)),
                expensesPaid, operating, fees, refunds, cogs, result);
    }

    // ------------------------------------------------------------------ auxiliares

    private FinancialEntry find(Long storeId, Long id) {
        return entries.findByIdAndStoreId(id, storeId).orElseThrow(() -> Errors.notFound("Lançamento"));
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static LocalDate bucket(LocalDate d, boolean monthly) {
        return monthly ? d.withDayOfMonth(1) : d;
    }

    private static String appendNote(String notes, String add) {
        return notes == null ? add : notes + " | " + add;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
