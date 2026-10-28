package com.lojafacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.lojafacil.finance.EntryType;
import com.lojafacil.finance.FinanceDtos.EntryRequest;
import com.lojafacil.finance.FinanceDtos.EntryView;
import com.lojafacil.finance.FinanceService;
import com.lojafacil.finance.FinancialEntry;
import com.lojafacil.inventory.InventoryService;
import com.lojafacil.shared.error.ApiException;
import com.lojafacil.support.IntegrationTest;
import com.lojafacil.support.TestData;

@Import(TestData.class)
class FinanceAndInventoryIT extends IntegrationTest {

    @Autowired
    FinanceService finance;
    @Autowired
    InventoryService inventory;

    @Test
    void parcelamentoPreservaOTotalEBaixaPorParcela() {
        TestData.StoreCtx s = data.newStore();
        Long rent = categoryId(s.storeId(), "Aluguel");
        List<EntryView> parts = finance.create(s.storeId(), new EntryRequest(EntryType.EXPENSE, rent, "Reforma",
                new BigDecimal("1000.00"), 3, LocalDate.now(), false, null, null, null, null, null));
        assertThat(parts).extracting(e -> e.amount().toPlainString()).containsExactly("333.34", "333.33", "333.33");
        assertThat(parts.get(2).dueDate()).isEqualTo(LocalDate.now().plusMonths(2));

        EntryView paid = finance.settle(s.storeId(), parts.get(0).id(), LocalDate.now());
        assertThat(paid.status()).isEqualTo(FinancialEntry.Status.PAID);
        assertThatThrownBy(() -> finance.settle(s.storeId(), parts.get(0).id(), LocalDate.now()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void estornoPreservaHistoricoENaoPodeSerRepetido() {
        TestData.StoreCtx s = data.newStore();
        Long mkt = categoryId(s.storeId(), "Marketing");
        EntryView e = finance.create(s.storeId(), new EntryRequest(EntryType.EXPENSE, mkt, "Anúncio",
                new BigDecimal("150.00"), 1, LocalDate.now(), true, LocalDate.now(), null, null, null, null)).get(0);
        var before = finance.summary(s.storeId(), LocalDate.now().minusDays(1), LocalDate.now());
        assertThat(before.expensesPaid()).isEqualByComparingTo("150.00");

        EntryView reversal = finance.reverse(s.storeId(), e.id(), "duplicado");
        assertThat(reversal.type()).isEqualTo(EntryType.INCOME);
        assertThat(reversal.reversalOfId()).isEqualTo(e.id());
        assertThatThrownBy(() -> finance.reverse(s.storeId(), e.id(), "de novo")).isInstanceOf(ApiException.class);

        var after = finance.summary(s.storeId(), LocalDate.now().minusDays(1), LocalDate.now());
        assertThat(after.expensesPaid()).isEqualByComparingTo("0.00");
        assertThat(after.operatingExpenses()).isEqualByComparingTo("0.00");
    }

    @Test
    void estoqueNuncaFicaNegativo() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "10.00", null, "1.00", 2));
        assertThatThrownBy(() -> inventory.exit(s.storeId(), variantId, 3, "perda")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> inventory.adjust(s.storeId(), variantId, -1, "contagem")).isInstanceOf(ApiException.class);

        data.buy(s.slug(), variantId, 2); // reserva as 2 unidades
        assertThatThrownBy(() -> inventory.exit(s.storeId(), variantId, 1, "uso interno")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> inventory.adjust(s.storeId(), variantId, 1, "contagem")).isInstanceOf(ApiException.class);
        assertThat(data.variant(variantId).getPhysicalQty()).isEqualTo(2);
    }

    @Test
    void outraLojaNaoMovimentaEstoqueAlheio() {
        TestData.StoreCtx a = data.newStore();
        TestData.StoreCtx b = data.newStore();
        Long variantA = data.variantOf(data.product(a.storeId(), "10.00", null, "1.00", 2));
        assertThatThrownBy(() -> inventory.entry(b.storeId(), variantA, 5, "tentativa", null))
                .isInstanceOf(ApiException.class).hasMessageContaining("não encontrad");
        assertThat(data.variant(variantA).getPhysicalQty()).isEqualTo(2);
    }

    private Long categoryId(Long storeId, String name) {
        return finance.listCategories(storeId).stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
    }
}
