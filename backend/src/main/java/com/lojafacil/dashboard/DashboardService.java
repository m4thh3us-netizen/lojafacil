package com.lojafacil.dashboard;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.auth.Role;
import com.lojafacil.catalog.VariantRepository;
import com.lojafacil.finance.FinanceDtos.CashFlow;
import com.lojafacil.finance.FinanceDtos.Summary;
import com.lojafacil.finance.FinanceService;
import com.lojafacil.inventory.InventoryQueries.StockView;
import com.lojafacil.orders.OrderRepository;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.money.Money;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public static final Map<String, String> DEFINITIONS = new LinkedHashMap<>();

    static {
        DEFINITIONS.put("salesRevenue", "Faturamento: soma dos pedidos confirmados (pagos) no período, pela data de confirmação. Não é dinheiro em caixa nem lucro.");
        DEFINITIONS.put("receipts", "Recebimentos: entradas efetivamente recebidas no período (vendas pagas e outras receitas baixadas).");
        DEFINITIONS.put("receivablesOpen", "A receber: contas a receber pendentes, de qualquer vencimento.");
        DEFINITIONS.put("expensesPaid", "Despesas pagas: despesas operacionais baixadas no período (sem taxas e reembolsos, mostrados à parte).");
        DEFINITIONS.put("estimatedResult", "Resultado estimado: faturamento - custo das mercadorias vendidas - taxas - reembolsos - despesas operacionais com vencimento no período. Estimativa gerencial, não é resultado contábil.");
        DEFINITIONS.put("pendingOrders", "Pedidos pendentes: abertos ou confirmados que ainda não foram entregues.");
        DEFINITIONS.put("lowStock", "Estoque baixo: variantes com saldo disponível (físico - reservado) igual ou abaixo do estoque mínimo.");
        DEFINITIONS.put("topProducts", "Mais vendidos: quantidade em pedidos confirmados no período.");
    }

    public record TopProduct(Long productId, String name, long quantity, BigDecimal revenue) {
    }

    public record SalesPoint(LocalDate date, long orders, BigDecimal revenue) {
    }

    public record Dashboard(LocalDate from, LocalDate to, boolean financialAccess, Summary summary,
                            long salesCount, long pendingOrders, long attentionOrders,
                            List<StockView> lowStock, List<TopProduct> topProducts, List<SalesPoint> sales,
                            CashFlow cashFlow, Map<String, String> definitions, boolean demo) {
    }

    private final FinanceService finance;
    private final OrderRepository orders;
    private final VariantRepository variants;
    private final EntityManager em;

    public DashboardService(FinanceService finance, OrderRepository orders, VariantRepository variants, EntityManager em) {
        this.finance = finance;
        this.orders = orders;
        this.variants = variants;
        this.em = em;
    }

    public Dashboard build(Long storeId, Role role, LocalDate from, LocalDate to, boolean demo) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw Errors.badRequest("INVALID_PERIOD", "Escolha um período de até 1 ano.");
        }
        boolean financial = role != Role.OPERATOR;
        var start = from.atStartOfDay(ZONE).toInstant();
        var end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        return new Dashboard(from, to, financial,
                financial ? finance.summary(storeId, from, to) : null,
                orders.salesCount(storeId, start, end),
                orders.countPending(storeId),
                orders.countAttention(storeId),
                variants.findLowStock(storeId, PageRequest.of(0, 8)).map(StockView::of).getContent(),
                topProducts(storeId, start, end, financial),
                salesSeries(storeId, from, to, financial),
                financial ? finance.cashFlow(storeId, from, to) : null,
                DEFINITIONS, demo);
    }

    private List<TopProduct> topProducts(Long storeId, java.time.Instant start, java.time.Instant end, boolean financial) {
        List<Object[]> rows = em.createQuery("""
                        select i.productId, max(i.productName), sum(i.quantity), sum(i.lineTotal) from OrderItem i
                        where i.order.storeId = :storeId
                          and i.order.status in (com.lojafacil.orders.OrderStatus.CONFIRMED, com.lojafacil.orders.OrderStatus.COMPLETED)
                          and i.order.confirmedAt >= :start and i.order.confirmedAt < :end
                        group by i.productId order by sum(i.quantity) desc
                        """, Object[].class)
                .setParameter("storeId", storeId).setParameter("start", start).setParameter("end", end)
                .setMaxResults(5).getResultList();
        return rows.stream().map(r -> new TopProduct((Long) r[0], (String) r[1], ((Number) r[2]).longValue(),
                financial ? Money.normalize((BigDecimal) r[3]) : null)).toList();
    }

    @SuppressWarnings("unchecked")
    private List<SalesPoint> salesSeries(Long storeId, LocalDate from, LocalDate to, boolean financial) {
        List<Object[]> rows = em.createNativeQuery("""
                        select cast(o.confirmed_at at time zone 'America/Sao_Paulo' as date) as d, count(*), coalesce(sum(o.total), 0)
                        from orders o
                        where o.store_id = :storeId and o.status in ('CONFIRMED', 'COMPLETED')
                          and o.confirmed_at >= :start and o.confirmed_at < :end
                        group by d order by d
                        """)
                .setParameter("storeId", storeId)
                .setParameter("start", from.atStartOfDay(ZONE).toOffsetDateTime())
                .setParameter("end", to.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime())
                .getResultList();
        Map<LocalDate, SalesPoint> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new SalesPoint(d, 0, financial ? Money.ZERO : null));
        }
        for (Object[] r : rows) {
            LocalDate d = r[0] instanceof Date sql ? sql.toLocalDate() : (LocalDate) r[0];
            byDay.put(d, new SalesPoint(d, ((Number) r[1]).longValue(), financial ? Money.normalize((BigDecimal) r[2]) : null));
        }
        return new ArrayList<>(byDay.values());
    }
}
